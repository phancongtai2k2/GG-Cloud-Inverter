using System.Collections.Concurrent;
using System.Text.Json;
using System.Threading.Channels;
using Dapper;
using GgPower.Domain;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Npgsql;
using NpgsqlTypes;

namespace GgPower.Infrastructure;

public sealed class IngestOptions
{
    /// <summary>Tenants (second topic level) this backend accepts, e.g. ["ggp"].</summary>
    public string[] Tenants { get; set; } = [];

    /// <summary>Configured tenants without duplicates; "ggp" when none is configured.</summary>
    public string[] ActiveTenants => Tenants.Length == 0 ? ["ggp"] : Tenants.Distinct().ToArray();
    public int RawRetentionDays { get; set; } = 90;
    public string DefaultTimeZone { get; set; } = "Asia/Ho_Chi_Minh";
    public int QueueCapacity { get; set; } = 20000;

    /// <summary>
    /// A fault that returns within this time after it cleared continues the same alert (counted, no new
    /// notification), unless the user marked that alert as resolved.
    /// </summary>
    public int AlertReopenMinutes { get; set; } = 60;
}

/// <summary>
/// Remembers which devices received samples for a time that was already aggregated (stored samples sent
/// after an outage, rows written late), so the roll-ups for that time are computed again. Each
/// recalculation raises the device's data version, which is part of the statistics cache keys.
/// </summary>
public sealed class BackfillTracker
{
    private readonly object _gate = new();
    private DateTimeOffset? _from;
    private readonly HashSet<string> _serials = new(StringComparer.OrdinalIgnoreCase);
    private readonly ConcurrentDictionary<string, int> _versions = new(StringComparer.OrdinalIgnoreCase);
    private long _samples;

    /// <summary>Stored samples accepted since start-up.</summary>
    public long Samples => Interlocked.Read(ref _samples);
    public void Count(int n) => Interlocked.Add(ref _samples, n);

    public void Request(string serial, DateTimeOffset ts)
    {
        lock (_gate)
        {
            if (_from is null || ts < _from) _from = ts;
            _serials.Add(serial);
        }
    }

    public (DateTimeOffset From, string[] Serials)? Take()
    {
        lock (_gate)
        {
            if (_from is not { } from) return null;
            var serials = _serials.ToArray();
            _from = null;
            _serials.Clear();
            return (from, serials);
        }
    }

    public void Bump(IEnumerable<string> serials)
    {
        foreach (var s in serials) _versions.AddOrUpdate(s, 1, (_, v) => v + 1);
    }

    public int Version(string? serial) => serial is not null && _versions.TryGetValue(serial, out var v) ? v : 0;
}

/// <summary>The one place that opens an alert, so a repeated fault never produces a second one.</summary>
public static class AlertStore
{
    /// <summary>
    /// Returns true only when a new alert was created. The same fault (device + code) does not create one
    /// while its alert is still open, and continues the previous alert when it comes back within
    /// <paramref name="reopenWindow"/> of clearing, unless the user marked that alert as resolved.
    /// </summary>
    public static async Task<bool> RaiseAsync(
        NpgsqlConnection c, string serial, string alarmId, string code, string severity, string title, string description,
        DateTimeOffset ts, string? snapshot, TimeSpan reopenWindow)
    {
        // A datalogger that restarts reports every fault that is still present again, under a new id.
        if (await c.ExecuteScalarAsync<bool>(
                "SELECT EXISTS (SELECT 1 FROM alerts WHERE serial = @serial AND code = @code AND ended_at IS NULL)", new { serial, code }))
            return false;

        var reopened = await c.ExecuteAsync(
            """
            UPDATE alerts SET ended_at = NULL, occurrences = occurrences + 1, last_triggered_at = @ts
            WHERE id = (SELECT id FROM alerts WHERE serial = @serial AND code = @code ORDER BY started_at DESC LIMIT 1)
              AND NOT resolved AND ended_at <= @ts AND ended_at >= @since
            """, new { serial, code, ts, since = ts - reopenWindow });
        if (reopened > 0) return false;

        const string insert =
            """
            INSERT INTO alerts (id, serial, alarm_id, code, severity, title, description, started_at, last_triggered_at, snapshot)
            VALUES (@id, @serial, @alarmId, @code, @severity, @title, @description, @ts, @ts, @snapshot::jsonb)
            ON CONFLICT (serial, alarm_id) DO NOTHING
            """;
        if (await c.ExecuteAsync(insert, new { id = Guid.NewGuid(), serial, alarmId, code, severity, title, description, ts, snapshot }) > 0) return true;
        // The device's daily counter restarts with the device, so an id can come back for a different
        // event. Same id and same start time is a redelivery; otherwise store it under a unique id.
        if (await c.ExecuteScalarAsync<bool>(
                "SELECT EXISTS (SELECT 1 FROM alerts WHERE serial = @serial AND alarm_id = @alarmId AND started_at = @ts)", new { serial, alarmId, ts }))
            return false;
        return await c.ExecuteAsync(insert, new
        {
            id = Guid.NewGuid(), serial, alarmId = $"{alarmId}-{ts.ToUnixTimeSeconds()}", code, severity, title, description, ts, snapshot,
        }) > 0;
    }
}

/// <summary>Hook for things that react to device events (push notifications, command tracking).</summary>
public interface IDeviceEventSink
{
    void OnAck(CommandAck ack) { }
    void OnConfigState(string serial, JsonElement state) { }
    Task OnAlarmAsync(AlarmEvent alarm, bool isNew, CancellationToken ct) => Task.CompletedTask;
}

/// <summary>
/// Turns MQTT messages into state: updates the in-memory cache, pushes to WebSocket subscribers at
/// once, and queues rows for the batch writer. Independent of the MQTT client so tests can feed it.
/// </summary>
public sealed class IngestPipeline(
    Db db, LiveStateCache live, RealtimeHub hub, IOptions<IngestOptions> options, IEnumerable<IDeviceEventSink> sinks,
    BackfillTracker backfill, TimeProvider clock, ILogger<IngestPipeline> log)
{
    private readonly TimeSpan _alertReopen = TimeSpan.FromMinutes(Math.Max(0, options.Value.AlertReopenMinutes));
    private readonly IDeviceEventSink[] _sinks = sinks.ToArray();
    private readonly string[] _tenants = options.Value.ActiveTenants;
    // When the database is unavailable for long, the oldest queued samples are given up so that
    // status messages and command acknowledgements keep flowing.
    private long _dropped;
    private Channel<object>? _rowsChannel;
    private Channel<object> _rows => _rowsChannel ??= Channel.CreateBounded<object>(
        new BoundedChannelOptions(options.Value.QueueCapacity) { FullMode = BoundedChannelFullMode.DropOldest, SingleReader = true },
        _ => Interlocked.Increment(ref _dropped));

    internal ChannelReader<object> Rows => _rows.Reader;
    public long Dropped => Interlocked.Read(ref _dropped);
    public long Received { get; private set; }
    public long Rejected { get; private set; }

    /// <param name="retained">
    /// True for a message the broker replays from its retained store when we subscribe. It carries state
    /// (settings, firmware, last status) but is not a sign of life: a device that is switched off still has them.
    /// </param>
    public async Task HandleAsync(string topic, ReadOnlyMemory<byte> payload, CancellationToken ct, bool retained = false)
    {
        _replayed = retained;
        Received++;
        var now = clock.GetUtcNow();
        if (UpTopic.Parse(topic) is not { } t || !_tenants.Contains(t.Tenant) || payload.Length is 0 or > 16 * 1024)
        {
            Rejected++;
            return;
        }

        JsonDocument doc;
        try { doc = JsonDocument.Parse(payload); }
        catch (JsonException) { Rejected++; return; }
        using (doc)
        {
            var j = doc.RootElement;
            var d = live.GetOrAdd(t.Serial, t.Tenant, out var created);
            if (created) await EnsureDeviceRowAsync(t.Serial, t.Tenant, ct);

            switch (t.Kind)
            {
                case "telemetry" when Payloads.Telemetry(j, t.Serial, now) is { } s:
                    lock (d.Gate)
                    {
                        var dt = d.Telemetry is { } prev ? Math.Clamp((s.Ts - prev.Ts).TotalSeconds, 0, 30) : 0;
                        d.Telemetry = s;
                        // Nothing is stored in a battery the inverter reports as absent.
                        if (d.BatteryPresent != false)
                            (d.BattFromPvKwh, d.BattFromGridKwh) =
                                BatteryOrigin.Step(d.BattFromPvKwh, d.BattFromGridKwh, s.PvW, s.BattW, s.Soc, d.BatteryCapacityKwh, dt);
                        Touch(d, now);
                    }
                    _rows.Writer.TryWrite(s);
                    PushSnapshot(d, now);
                    break;

                case "battery" when Payloads.Battery(j, t.Serial, now) is { } b:
                    lock (d.Gate)
                    {
                        d.Battery = b;
                        if (b is { CapNominalAh: > 0, Voltage: > 0 } && b.Present != false)
                            d.BatteryCapacityKwh = Math.Round(b.CapNominalAh.Value * b.Voltage.Value / 1000, 1);
                        Touch(d, now);
                    }
                    _rows.Writer.TryWrite(b);
                    PushSnapshot(d, now);
                    break;

                // Samples the datalogger stored while its uplink was down. They fill the history and the
                // statistics; the live state shown in the app is not touched by them.
                case "backfill" when !retained:
                    var span = Math.Clamp(Payloads.Int(j, "interval_s") ?? 120, 1, 900);
                    var accepted = 0;
                    if (j.TryGetProperty("telemetry", out var stored) && stored.ValueKind == JsonValueKind.Array)
                        foreach (var e in stored.EnumerateArray().Take(60))
                            // a stored sample without any measurement says nothing about that time
                            if (Payloads.Telemetry(e, t.Serial, now, span) is { } s && (s.InvStatus ?? s.PvW ?? s.GridW ?? s.LoadW ?? s.GridV) is not null
                                && _rows.Writer.TryWrite(s)) accepted++;
                    if (j.TryGetProperty("battery", out var storedBatt) && storedBatt.ValueKind == JsonValueKind.Array)
                        foreach (var e in storedBatt.EnumerateArray().Take(60))
                            if (Payloads.Battery(e, t.Serial, now, backfill: true) is { } b && (b.Voltage ?? b.Soc ?? b.CurrentA) is not null
                                && _rows.Writer.TryWrite(b)) accepted++;
                    lock (d.Gate) Touch(d, now);
                    if (accepted == 0) { Rejected++; break; }
                    backfill.Count(accepted);
                    log.LogInformation("backfill from {Serial}: {Count} stored samples, {Remaining} to come", t.Serial, accepted, Payloads.Int(j, "remaining") ?? 0);
                    break;

                case "status" when Payloads.Status(j, t.Serial, now) is { } st:
                    await OnStatusAsync(d, st, j, now, ct);
                    break;

                case "config/state":
                    var state = j.Clone();
                    lock (d.Gate) { d.ConfigState = state; d.ConfigStateAt = now; Touch(d, now); }
                    await ExecAsync("UPDATE devices SET config_state = @v::jsonb, config_state_at = @now WHERE serial = @serial",
                        new { v = state.GetRawText(), now, serial = d.Serial }, ct);
                    foreach (var s in _sinks) s.OnConfigState(d.Serial, state);
                    hub.Publish(d.Serial, LiveJson.Bytes(new { type = "config_changed", serial = d.Serial }));
                    break;

                case "firmware":
                    var fw = j.Clone();
                    lock (d.Gate) { d.Firmware = fw; Touch(d, now); }
                    await ExecAsync("UPDATE devices SET firmware_json = @v::jsonb WHERE serial = @serial", new { v = fw.GetRawText(), serial = d.Serial }, ct);
                    break;

                case "alarm" when Payloads.Alarm(j, t.Serial, now) is { } a:
                    await OnAlarmAsync(d, a, now, ct);
                    break;

                case "command/ack" when Payloads.Ack(j, t.Serial) is { } ack:
                    lock (d.Gate) Touch(d, now);
                    foreach (var s in _sinks) s.OnAck(ack);
                    break;

                default:
                    Rejected++;
                    break;
            }
        }
    }

    // Messages are handled one at a time by the MQTT client, so a field is enough to carry the flag.
    private bool _replayed;

    private void Touch(DeviceLive d, DateTimeOffset now)
    {
        d.Dirty = true;
        if (_replayed) return;
        d.LastSeen = now;
        d.Online = true;
    }

    private void PushSnapshot(DeviceLive d, DateTimeOffset now)
    {
        if (hub.HasListeners(d.Serial)) hub.Publish(d.Serial, LiveJson.Bytes(LiveJson.Snapshot(d, now)));
    }

    private async Task OnStatusAsync(DeviceLive d, DeviceStatus st, JsonElement raw, DateTimeOffset now, CancellationToken ct)
    {
        lock (d.Gate)
        {
            // The device's own "offline" messages (last will, topic change) do not count as activity.
            if (st.Online)
            {
                Touch(d, now);
                if (_replayed) d.Online = true;   // last known status; reachability still needs a live message
                d.Model = st.Model ?? d.Model;
                d.InverterFw = st.InverterFw ?? d.InverterFw;
                d.LoggerFw = st.LoggerFw ?? d.LoggerFw;
                d.InverterComm = st.InverterComm ?? d.InverterComm;
            }
            else
            {
                d.Online = false;
                d.Dirty = true;
            }
        }
        await ExecAsync(
            """
            UPDATE devices SET online = @online, status_json = @raw::jsonb,
                   model = COALESCE(@model, model), inverter_fw = COALESCE(@fw, inverter_fw),
                   logger_fw = COALESCE(@logger, logger_fw), inverter_comm = COALESCE(@comm, inverter_comm),
                   last_seen = CASE WHEN @online AND NOT @replayed THEN @now ELSE last_seen END
            WHERE serial = @serial
            """,
            new { online = st.Online, raw = raw.GetRawText(), model = st.Model, fw = st.InverterFw, logger = st.LoggerFw, comm = st.InverterComm, now, serial = d.Serial, replayed = _replayed }, ct);
        hub.Publish(d.Serial, LiveJson.Bytes(LiveJson.Snapshot(d, now)));
    }

    private async Task OnAlarmAsync(DeviceLive d, AlarmEvent a, DateTimeOffset now, CancellationToken ct)
    {
        var isNew = false;
        await using (var c = await db.OpenAsync(ct))
        {
            if (a.Triggered)
            {
                isNew = await AlertStore.RaiseAsync(c, a.Serial, a.AlarmId, a.Code, a.Severity, a.Title, a.Description, a.Ts, a.Snapshot?.GetRawText(), _alertReopen);
            }
            else
            {
                await c.ExecuteAsync("UPDATE alerts SET ended_at = @Ts WHERE serial = @Serial AND code = @Code AND ended_at IS NULL", new { a.Ts, a.Serial, a.Code });
            }
            await RecountAsync(c, d);
        }
        lock (d.Gate) Touch(d, now);
        hub.Publish(d.Serial, LiveJson.Bytes(new { type = "alerts_changed", serial = d.Serial }));
        PushSnapshot(d, now);
        foreach (var s in _sinks)
        {
            try { await s.OnAlarmAsync(a, isNew, ct); }
            catch (Exception ex) { log.LogWarning(ex, "alarm sink failed"); }
        }
    }

    public static async Task RecountAsync(NpgsqlConnection c, DeviceLive d)
    {
        var r = await c.QuerySingleAsync<(int Errors, int Warnings, int Unresolved)>(
            """
            SELECT count(*) FILTER (WHERE ended_at IS NULL AND severity = 'error')::int,
                   count(*) FILTER (WHERE ended_at IS NULL AND severity = 'warning')::int,
                   count(*) FILTER (WHERE NOT resolved)::int
            FROM alerts WHERE serial = @serial
            """, new { serial = d.Serial });
        // The backend's own "connection lost" alert is not an inverter fault.
        var active = (await c.QueryAsync<ActiveError>(
            "SELECT code, title FROM alerts WHERE serial = @serial AND ended_at IS NULL AND code <> 'OFFLINE' ORDER BY started_at",
            new { serial = d.Serial })).ToArray();
        lock (d.Gate) { d.OpenErrors = r.Errors; d.OpenWarnings = r.Warnings; d.Unresolved = r.Unresolved; d.ActiveErrors = active; }
    }

    private async Task EnsureDeviceRowAsync(string serial, string tenant, CancellationToken ct) =>
        await ExecAsync("INSERT INTO devices (serial, tenant) VALUES (@serial, @tenant) ON CONFLICT (serial) DO NOTHING", new { serial, tenant }, ct);

    private async Task ExecAsync(string sql, object args, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        await c.ExecuteAsync(new CommandDefinition(sql, args, cancellationToken: ct));
    }

    /// <summary>Loads devices and alert counters into the cache at start-up.</summary>
    public async Task WarmUpAsync(CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        var rows = await c.QueryAsync(
            """
            SELECT d.serial, d.tenant, d.model, d.inverter_fw, d.logger_fw, d.inverter_comm, d.last_seen, d.online,
                   d.config_state::text AS config_state, d.config_state_at, d.firmware_json::text AS firmware_json,
                   d.rated_power_kw, d.battery_capacity_kwh, d.batt_from_pv_kwh, d.batt_from_grid_kwh,
                   (SELECT count(*) FROM alerts a WHERE a.serial = d.serial AND a.ended_at IS NULL AND a.severity = 'error')::int AS open_errors,
                   (SELECT count(*) FROM alerts a WHERE a.serial = d.serial AND a.ended_at IS NULL AND a.severity = 'warning')::int AS open_warnings,
                   (SELECT count(*) FROM alerts a WHERE a.serial = d.serial AND NOT a.resolved)::int AS unresolved
            FROM devices d
            """);
        foreach (var r in rows)
        {
            live.Load(new DeviceLive
            {
                Serial = r.serial, Tenant = r.tenant, Model = r.model, InverterFw = r.inverter_fw, LoggerFw = r.logger_fw,
                InverterComm = r.inverter_comm, LastSeen = new DateTimeOffset((DateTime)r.last_seen, TimeSpan.Zero), Online = r.online,
                ConfigState = r.config_state is string cs ? JsonDocument.Parse(cs).RootElement.Clone() : null,
                ConfigStateAt = r.config_state_at is DateTime at ? new DateTimeOffset(at, TimeSpan.Zero) : null,
                Firmware = r.firmware_json is string fj ? JsonDocument.Parse(fj).RootElement.Clone() : null,
                RatedPowerKw = r.rated_power_kw, BatteryCapacityKwh = r.battery_capacity_kwh,
                BattFromPvKwh = r.batt_from_pv_kwh, BattFromGridKwh = r.batt_from_grid_kwh,
                OpenErrors = r.open_errors, OpenWarnings = r.open_warnings, Unresolved = r.unresolved,
            });
        }
        foreach (var d in live.All) await RecountAsync(c, d);
    }
}

/// <summary>Writes queued samples in batches (COPY into a staging table, then insert-ignore-duplicates).</summary>
public sealed class BatchWriter(Db db, IngestPipeline pipeline, LiveStateCache live, BackfillTracker backfill, ILogger<BatchWriter> log) : BackgroundService
{
    public long Written { get; private set; }
    public double LastBatchMs { get; private set; }

    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        var telemetry = new List<TelemetrySample>(1024);
        var battery = new List<BatterySample>(256);
        var lastDeviceFlush = DateTime.UtcNow;
        while (!stop.IsCancellationRequested)
        {
            try
            {
                using var window = CancellationTokenSource.CreateLinkedTokenSource(stop);
                window.CancelAfter(TimeSpan.FromSeconds(1));
                try
                {
                    while (telemetry.Count + battery.Count < 5000 && await pipeline.Rows.WaitToReadAsync(window.Token))
                    {
                        while (telemetry.Count + battery.Count < 5000 && pipeline.Rows.TryRead(out var row))
                        {
                            if (row is TelemetrySample t) telemetry.Add(t); else if (row is BatterySample b) battery.Add(b);
                        }
                    }
                }
                catch (OperationCanceledException) when (!stop.IsCancellationRequested) { /* batch window elapsed */ }

                if (telemetry.Count + battery.Count > 0)
                {
                    var sw = System.Diagnostics.Stopwatch.StartNew();
                    await FlushAsync(telemetry, battery, stop);
                    // Rows for a time the roll-up has already passed (stored samples, or a batch that had to wait
                    // for the database) make it compute that time again.
                    var late = DateTimeOffset.UtcNow.AddMinutes(-10);
                    foreach (var t in telemetry) if (t.SpanS is not null || t.Ts < late) backfill.Request(t.Serial, t.Ts);
                    foreach (var b in battery) if (b.Ts < late) backfill.Request(b.Serial, b.Ts);
                    LastBatchMs = sw.Elapsed.TotalMilliseconds;
                    Written += telemetry.Count + battery.Count;
                    telemetry.Clear();
                    battery.Clear();
                }
                if ((DateTime.UtcNow - lastDeviceFlush).TotalSeconds >= 15)
                {
                    lastDeviceFlush = DateTime.UtcNow;
                    await FlushDevicesAsync(stop);
                }
            }
            catch (OperationCanceledException) when (stop.IsCancellationRequested) { break; }
            catch (Exception ex)
            {
                // Keep the batch and retry: losing samples is worse than a short delay.
                log.LogError(ex, "batch write failed ({Count} rows kept for retry)", telemetry.Count + battery.Count);
                if (telemetry.Count + battery.Count > 50000) { telemetry.Clear(); battery.Clear(); }
                await Task.Delay(2000, stop).ContinueWith(_ => { }, CancellationToken.None);
            }
        }
    }

    private async Task FlushAsync(List<TelemetrySample> telemetry, List<BatterySample> battery, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        await using var tx = await c.BeginTransactionAsync(ct);
        if (telemetry.Count > 0)
        {
            await c.ExecuteAsync("CREATE TEMP TABLE stage_t (LIKE telemetry_raw) ON COMMIT DROP", transaction: tx);
            await using (var w = await c.BeginBinaryImportAsync(
                "COPY stage_t (serial, ts, inv_status, inv_temp, pv1_v, pv1_a, pv1_w, pv2_v, pv2_a, pv2_w, pv_w, grid_v, grid_a, grid_hz, grid_w, load_v, load_a, load_hz, load_w, batt_w, soc, e_yield_today, e_yield_total, e_load_today, e_export_today, e_import_today, span_s, batt_present) FROM STDIN (FORMAT BINARY)", ct))
            {
                foreach (var s in telemetry)
                {
                    await w.StartRowAsync(ct);
                    await w.WriteAsync(s.Serial, NpgsqlDbType.Text, ct);
                    await w.WriteAsync(s.Ts.UtcDateTime, NpgsqlDbType.TimestampTz, ct);
                    if (s.InvStatus is { } st) await w.WriteAsync((short)st, NpgsqlDbType.Smallint, ct); else await w.WriteNullAsync(ct);
                    foreach (var v in new[] { s.InvTemp, s.Pv1V, s.Pv1A, s.Pv1W, s.Pv2V, s.Pv2A, s.Pv2W, s.PvW, s.GridV, s.GridA, s.GridHz, s.GridW,
                                 s.LoadV, s.LoadA, s.LoadHz, s.LoadW, s.BattW, s.Soc, s.YieldToday, s.YieldTotal, s.LoadToday, s.ExportToday, s.ImportToday })
                    {
                        if (v is { } x) await w.WriteAsync((float)x, NpgsqlDbType.Real, ct); else await w.WriteNullAsync(ct);
                    }
                    if (s.SpanS is { } span) await w.WriteAsync((short)span, NpgsqlDbType.Smallint, ct); else await w.WriteNullAsync(ct);
                    if (s.BatteryPresent is { } present) await w.WriteAsync(present, NpgsqlDbType.Boolean, ct); else await w.WriteNullAsync(ct);
                }
                await w.CompleteAsync(ct);
            }
            await c.ExecuteAsync("INSERT INTO telemetry_raw SELECT * FROM stage_t ON CONFLICT DO NOTHING", transaction: tx);
        }
        if (battery.Count > 0)
        {
            await c.ExecuteAsync("CREATE TEMP TABLE stage_b (LIKE battery_raw) ON COMMIT DROP", transaction: tx);
            await using (var w = await c.BeginBinaryImportAsync(
                "COPY stage_b (serial, ts, soc, soh, voltage, current_a, power_w, temp_cells, temp_bms, cycles, cap_remaining_ah, cap_nominal_ah, est_minutes, present, bms_comm) FROM STDIN (FORMAT BINARY)", ct))
            {
                foreach (var b in battery)
                {
                    await w.StartRowAsync(ct);
                    await w.WriteAsync(b.Serial, NpgsqlDbType.Text, ct);
                    await w.WriteAsync(b.Ts.UtcDateTime, NpgsqlDbType.TimestampTz, ct);
                    foreach (var v in new[] { b.Soc, b.Soh, b.Voltage, b.CurrentA, b.PowerW, b.TempCells, b.TempBms })
                    {
                        if (v is { } x) await w.WriteAsync((float)x, NpgsqlDbType.Real, ct); else await w.WriteNullAsync(ct);
                    }
                    if (b.Cycles is { } cy) await w.WriteAsync(cy, NpgsqlDbType.Integer, ct); else await w.WriteNullAsync(ct);
                    foreach (var v in new[] { b.CapRemainingAh, b.CapNominalAh, b.EstMinutes })
                    {
                        if (v is { } x) await w.WriteAsync((float)x, NpgsqlDbType.Real, ct); else await w.WriteNullAsync(ct);
                    }
                    if (b.Present is { } present) await w.WriteAsync(present, NpgsqlDbType.Boolean, ct); else await w.WriteNullAsync(ct);
                    if (b.BmsComm is { } comm) await w.WriteAsync(comm, NpgsqlDbType.Text, ct); else await w.WriteNullAsync(ct);
                }
                await w.CompleteAsync(ct);
            }
            await c.ExecuteAsync("INSERT INTO battery_raw SELECT * FROM stage_b ON CONFLICT DO NOTHING", transaction: tx);
        }
        await tx.CommitAsync(ct);
    }

    private async Task FlushDevicesAsync(CancellationToken ct)
    {
        var dirty = new List<object>();
        foreach (var d in live.All)
        {
            lock (d.Gate)
            {
                if (!d.Dirty) continue;
                d.Dirty = false;
                dirty.Add(new { serial = d.Serial, online = d.Online, seen = d.LastSeen, cap = d.BatteryCapacityKwh, pv = d.BattFromPvKwh, grid = d.BattFromGridKwh });
            }
        }
        if (dirty.Count == 0) return;
        await using var c = await db.OpenAsync(ct);
        await c.ExecuteAsync(
            """
            UPDATE devices SET online = @online, last_seen = GREATEST(last_seen, @seen),
                   battery_capacity_kwh = COALESCE(@cap, battery_capacity_kwh), batt_from_pv_kwh = @pv, batt_from_grid_kwh = @grid
            WHERE serial = @serial
            """, dirty);
    }
}

/// <summary>Aggregates raw samples into 15-minute and daily rows, and maintains partitions.</summary>
public sealed class RollupService(Db db, PartitionMaintenance partitions, BackfillTracker backfill, IOptions<IngestOptions> options, TimeProvider clock, ILogger<RollupService> log) : BackgroundService
{
    // "/*serials*/" becomes a filter when only some devices are recalculated.
    private static string For(string sql, string column, bool filtered) => sql.Replace("/*serials*/", filtered ? $"AND {column} = ANY(@serials)" : "");

    private const string Rollup15 =
        """
        WITH s AS (
            SELECT *, EXTRACT(EPOCH FROM ts - LAG(ts) OVER (PARTITION BY serial ORDER BY ts)) AS gap
            FROM telemetry_raw WHERE ts >= @from - interval '15 minutes' AND ts < @to /*serials*/
        ), g AS (
            -- Each sample stands for the time since the previous one. After a silence (or for the very first
            -- sample) that time is unknown, so only the nominal 5 s interval is counted, not the whole gap.
            -- A sample stored by the datalogger during an outage stands for its storage interval instead.
            SELECT *, CASE WHEN span_s IS NOT NULL THEN LEAST(span_s, COALESCE(gap, span_s))
                           WHEN gap IS NULL OR gap > 30 THEN 5 ELSE gap END AS dt FROM s
        ), e AS (
            SELECT *, GREATEST(pv_w, 0) AS pv, GREATEST(-grid_w, 0) AS exp, GREATEST(batt_w, 0) AS chg FROM g WHERE ts >= @from
        )
        INSERT INTO rollup_15m AS r (serial, bucket, samples, pv_w, grid_w, load_w, batt_w, soc,
            pv1_v, pv1_a, pv1_w, pv2_v, pv2_a, pv2_w, grid_v, grid_a, grid_hz, load_v, load_a, inv_temp,
            pv_kwh, load_kwh, import_kwh, export_kwh, charge_kwh, discharge_kwh, pv_self_kwh, pv_charge_kwh, pv_export_kwh)
        SELECT serial, date_bin('15 minutes', ts, TIMESTAMPTZ '2000-01-01') AS bucket, count(*),
            avg(pv_w), avg(grid_w), avg(load_w), avg(batt_w), avg(soc),
            avg(pv1_v), avg(pv1_a), avg(pv1_w), avg(pv2_v), avg(pv2_a), avg(pv2_w), avg(grid_v), avg(grid_a), avg(grid_hz), avg(load_v), avg(load_a), avg(inv_temp),
            COALESCE(sum(pv * dt), 0) / 3.6e6,
            COALESCE(sum(GREATEST(load_w, 0) * dt), 0) / 3.6e6,
            COALESCE(sum(GREATEST(grid_w, 0) * dt), 0) / 3.6e6,
            COALESCE(sum(exp * dt), 0) / 3.6e6,
            COALESCE(sum(chg * dt), 0) / 3.6e6,
            COALESCE(sum(GREATEST(-batt_w, 0) * dt), 0) / 3.6e6,
            COALESCE(sum((pv - LEAST(pv, exp) - LEAST(pv - LEAST(pv, exp), chg)) * dt), 0) / 3.6e6,
            COALESCE(sum(LEAST(pv - LEAST(pv, exp), chg) * dt), 0) / 3.6e6,
            COALESCE(sum(LEAST(pv, exp) * dt), 0) / 3.6e6
        FROM e GROUP BY 1, 2
        ON CONFLICT (serial, bucket) DO UPDATE SET
            samples = EXCLUDED.samples, pv_w = EXCLUDED.pv_w, grid_w = EXCLUDED.grid_w, load_w = EXCLUDED.load_w, batt_w = EXCLUDED.batt_w, soc = EXCLUDED.soc,
            pv1_v = EXCLUDED.pv1_v, pv1_a = EXCLUDED.pv1_a, pv1_w = EXCLUDED.pv1_w, pv2_v = EXCLUDED.pv2_v, pv2_a = EXCLUDED.pv2_a, pv2_w = EXCLUDED.pv2_w,
            grid_v = EXCLUDED.grid_v, grid_a = EXCLUDED.grid_a, grid_hz = EXCLUDED.grid_hz, load_v = EXCLUDED.load_v, load_a = EXCLUDED.load_a, inv_temp = EXCLUDED.inv_temp,
            pv_kwh = EXCLUDED.pv_kwh, load_kwh = EXCLUDED.load_kwh, import_kwh = EXCLUDED.import_kwh, export_kwh = EXCLUDED.export_kwh,
            charge_kwh = EXCLUDED.charge_kwh, discharge_kwh = EXCLUDED.discharge_kwh,
            pv_self_kwh = EXCLUDED.pv_self_kwh, pv_charge_kwh = EXCLUDED.pv_charge_kwh, pv_export_kwh = EXCLUDED.pv_export_kwh
        """;

    private const string RollupBattery =
        """
        UPDATE rollup_15m r SET batt_v = b.v, batt_a = b.a, batt_temp = b.t
        FROM (SELECT serial, date_bin('15 minutes', ts, TIMESTAMPTZ '2000-01-01') AS bucket, avg(voltage) AS v, avg(current_a) AS a, avg(temp_cells) AS t
              FROM battery_raw WHERE ts >= @from AND ts < @to /*serials*/ GROUP BY 1, 2) b
        WHERE r.serial = b.serial AND r.bucket = b.bucket
        """;

    public async Task RunOnceAsync(DateTimeOffset now, CancellationToken ct, TimeSpan? lookBack = null)
    {
        var from = Floor15(now - (lookBack ?? TimeSpan.FromMinutes(20)));
        await using var c = await db.OpenAsync(ct);
        // Daily rows are rebuilt from whole local days: start two days back at UTC midnight minus a day of margin.
        await RunAsync(c, from, now, new DateTimeOffset(now.UtcDateTime.Date.AddDays(-3), TimeSpan.Zero), null);

        // Samples that arrived for an older time: compute that time again, for those devices only.
        if (backfill.Take() is not { } req) return;
        try
        {
            await RunAsync(c, Floor15(req.From), now, new DateTimeOffset(req.From.UtcDateTime.Date.AddDays(-2), TimeSpan.Zero), req.Serials);
            backfill.Bump(req.Serials);
            log.LogInformation("recalculated statistics from {From:u} for {Count} device(s) after late samples", req.From, req.Serials.Length);
        }
        catch
        {
            foreach (var s in req.Serials) backfill.Request(s, req.From);   // try again on the next run
            throw;
        }
    }

    private async Task RunAsync(NpgsqlConnection c, DateTimeOffset from, DateTimeOffset now, DateTimeOffset dayFrom, string[]? serials)
    {
        var filtered = serials is not null;
        var args = new { from, to = now.AddMinutes(1), serials = serials ?? [] };
        await c.ExecuteAsync(new CommandDefinition(For(Rollup15, "serial", filtered), args, commandTimeout: 300));
        await c.ExecuteAsync(new CommandDefinition(For(RollupBattery, "serial", filtered), args, commandTimeout: 300));
        await c.ExecuteAsync(new CommandDefinition(For(RollupDaySimple, "r.serial", filtered),
            new { from = dayFrom, tz = options.Value.DefaultTimeZone, serials = serials ?? [] }, commandTimeout: 300));
    }

    // Rebuild the last local days completely (the first, possibly partial, day of the window is skipped).
    private const string RollupDaySimple =
        """
        INSERT INTO rollup_1d AS d (serial, day, pv_kwh, load_kwh, import_kwh, export_kwh, charge_kwh, discharge_kwh, pv_self_kwh, pv_charge_kwh, pv_export_kwh, soc_avg)
        SELECT serial, day, sum(pv_kwh), sum(load_kwh), sum(import_kwh), sum(export_kwh), sum(charge_kwh), sum(discharge_kwh),
               sum(pv_self_kwh), sum(pv_charge_kwh), sum(pv_export_kwh), avg(soc)
        FROM (
            SELECT r.*, (r.bucket AT TIME ZONE COALESCE(p.time_zone, @tz))::date AS day,
                   (@from AT TIME ZONE COALESCE(p.time_zone, @tz))::date AS first_day
            FROM rollup_15m r
            LEFT JOIN project_devices pd ON pd.serial = r.serial
            LEFT JOIN projects p ON p.id = pd.project_id
            WHERE r.bucket >= @from /*serials*/
        ) x
        WHERE day > first_day
        GROUP BY serial, day
        ON CONFLICT (serial, day) DO UPDATE SET
            pv_kwh = EXCLUDED.pv_kwh, load_kwh = EXCLUDED.load_kwh, import_kwh = EXCLUDED.import_kwh, export_kwh = EXCLUDED.export_kwh,
            charge_kwh = EXCLUDED.charge_kwh, discharge_kwh = EXCLUDED.discharge_kwh,
            pv_self_kwh = EXCLUDED.pv_self_kwh, pv_charge_kwh = EXCLUDED.pv_charge_kwh, pv_export_kwh = EXCLUDED.pv_export_kwh, soc_avg = EXCLUDED.soc_avg
        """;

    public static DateTimeOffset Floor15(DateTimeOffset t) =>
        new(t.UtcDateTime.Ticks - t.UtcDateTime.Ticks % TimeSpan.FromMinutes(15).Ticks, TimeSpan.Zero);

    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        var lastMaintenance = DateTimeOffset.MinValue;
        while (!stop.IsCancellationRequested)
        {
            try
            {
                var now = clock.GetUtcNow();
                if (now - lastMaintenance > TimeSpan.FromHours(6))
                {
                    await partitions.RunAsync(options.Value.RawRetentionDays, now, stop);
                    await using var c = await db.OpenAsync(stop);
                    await c.ExecuteAsync("DELETE FROM audit_log WHERE at < now() - interval '90 days'");
                    await c.ExecuteAsync("DELETE FROM otp_codes WHERE created_at < now() - interval '2 days'");
                    await c.ExecuteAsync("DELETE FROM sessions WHERE expires_at < now() - interval '7 days'");
                    await c.ExecuteAsync("DELETE FROM reset_tokens WHERE expires_at < now() - interval '1 day'");
                    lastMaintenance = now;
                }
                await RunOnceAsync(now, stop);
            }
            catch (OperationCanceledException) when (stop.IsCancellationRequested) { break; }
            catch (Exception ex) { log.LogError(ex, "rollup failed"); }
            await Task.Delay(TimeSpan.FromSeconds(30), stop).ContinueWith(_ => { }, CancellationToken.None);
        }
    }
}
