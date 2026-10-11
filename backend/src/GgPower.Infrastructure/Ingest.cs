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
/// <summary>
/// Devices with live samples written since the last roll-up, and the earliest sample time of each (plan A4). The roll-up
/// recomputes only those devices, from the 15-minute bucket / local day of that sample, instead of every device over
/// several days. Samples for an older time go through <see cref="BackfillTracker"/> instead.
/// </summary>
public sealed class RollupTracker
{
    private readonly ConcurrentDictionary<string, DateTimeOffset> _dirty = new(StringComparer.OrdinalIgnoreCase);

    public int Count => _dirty.Count;

    public void Mark(string serial, DateTimeOffset ts)
    {
        var utc = ts.ToUniversalTime();
        _dirty.AddOrUpdate(serial, utc, (_, cur) => utc < cur ? utc : cur);
    }

    /// <summary>Removes and returns the current set (a sample marked meanwhile lands in this set or the next one).</summary>
    public Dictionary<string, DateTimeOffset> Take()
    {
        var taken = new Dictionary<string, DateTimeOffset>(StringComparer.OrdinalIgnoreCase);
        foreach (var serial in _dirty.Keys)
            if (_dirty.TryRemove(serial, out var ts)) taken[serial] = ts;
        return taken;
    }

    /// <summary>Puts a set back after a failed run, so the next run covers it.</summary>
    public void Restore(IReadOnlyDictionary<string, DateTimeOffset> set)
    {
        foreach (var (serial, ts) in set) Mark(serial, ts);
    }
}

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
    BackfillTracker backfill, DeviceEventQueue events, TimeProvider clock, ILogger<IngestPipeline> log)
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
    /// <summary>Rows waiting for the batch writer, and how many fit before the oldest are dropped (readiness, metrics).</summary>
    public int QueueLength => _rows.Reader.CanCount ? _rows.Reader.Count : 0;
    public int QueueCapacity => options.Value.QueueCapacity;
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
                        // A sample older than the one shown (redelivered by the broker after a reconnect) is stored,
                        // but does not take the live values back in time.
                        if (d.Telemetry is not { } prev || s.Ts >= prev.Ts)
                        {
                            var dt = d.Telemetry is { } p ? Math.Clamp((s.Ts - p.Ts).TotalSeconds, 0, 30) : 0;
                            d.Telemetry = s;
                            // Nothing is stored in a battery the inverter reports as absent.
                            if (d.BatteryPresent != false)
                                (d.BattFromPvKwh, d.BattFromGridKwh) =
                                    BatteryOrigin.Step(d.BattFromPvKwh, d.BattFromGridKwh, s.PvW, s.BattW, s.Soc, d.BatteryCapacityKwh, dt);
                        }
                        Touch(d, now);
                    }
                    _rows.Writer.TryWrite(s);
                    PushSnapshot(d, now);
                    break;

                case "battery" when Payloads.Battery(j, t.Serial, now) is { } b:
                    lock (d.Gate)
                    {
                        if (d.Battery is not { } pb || b.Ts >= pb.Ts) d.Battery = b;
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
                    // Commands wait on the in-memory state; the database copy (read at start-up) may follow a moment later.
                    foreach (var s in _sinks) s.OnConfigState(d.Serial, state);
                    hub.Publish(d.Serial, LiveJson.Bytes(new { type = "config_changed", serial = d.Serial }));
                    var stateJson = state.GetRawText();
                    await events.EnqueueAsync("config/state", c => ExecAsync(
                        "UPDATE devices SET config_state = @v::jsonb, config_state_at = @now WHERE serial = @serial",
                        new { v = stateJson, now, serial = d.Serial }, c), mayDrop: false, ct);
                    break;

                case "firmware":
                    var fw = j.Clone();
                    lock (d.Gate) { d.Firmware = fw; Touch(d, now); }
                    var fwJson = fw.GetRawText();
                    await events.EnqueueAsync("firmware", c => ExecAsync("UPDATE devices SET firmware_json = @v::jsonb WHERE serial = @serial",
                        new { v = fwJson, serial = d.Serial }, c), mayDrop: true, ct);
                    break;

                // Alerts, counters and notifications go through the event queue; never dropped.
                case "alarm" when Payloads.Alarm(j, t.Serial, now) is { } a:
                    await events.EnqueueAsync("alarm", c => OnAlarmAsync(d, a, now, c), mayDrop: false, ct);
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
                d.LoggerIp = st.Ip ?? d.LoggerIp;
                d.LoggerRssi = st.Rssi ?? d.LoggerRssi;
                d.LoggerSn = st.LoggerSn ?? d.LoggerSn;
                d.LoggerConn = st.ConnType ?? d.LoggerConn;
            }
            else
            {
                d.Online = false;
                d.Dirty = true;
            }
        }
        hub.Publish(d.Serial, LiveJson.Bytes(LiveJson.Snapshot(d, now)));
        var args = new
        {
            online = st.Online, raw = raw.GetRawText(), model = st.Model, fw = st.InverterFw, logger = st.LoggerFw, comm = st.InverterComm,
            now, serial = d.Serial, replayed = _replayed,
        };
        // An "offline" status must reach the database (it decides the state after a restart); a newer "online" one
        // supersedes an older one, so those may be dropped when the queue is full.
        await events.EnqueueAsync("status", c => ExecAsync(
            """
            UPDATE devices SET online = @online, status_json = @raw::jsonb,
                   model = COALESCE(@model, model), inverter_fw = COALESCE(@fw, inverter_fw),
                   logger_fw = COALESCE(@logger, logger_fw), inverter_comm = COALESCE(@comm, inverter_comm),
                   last_seen = CASE WHEN @online AND NOT @replayed THEN @now ELSE last_seen END
            WHERE serial = @serial
            """, args, c), mayDrop: st.Online, ct);
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
                   d.rated_power_kw, d.battery_capacity_kwh, d.batt_from_pv_kwh, d.batt_from_grid_kwh, d.status_json::text AS status_json,
                   (SELECT count(*) FROM alerts a WHERE a.serial = d.serial AND a.ended_at IS NULL AND a.severity = 'error')::int AS open_errors,
                   (SELECT count(*) FROM alerts a WHERE a.serial = d.serial AND a.ended_at IS NULL AND a.severity = 'warning')::int AS open_warnings,
                   (SELECT count(*) FROM alerts a WHERE a.serial = d.serial AND NOT a.resolved)::int AS unresolved
            FROM devices d
            """);
        foreach (var r in rows)
        {
            // The datalogger link (IP, RSSI…) is only kept in the last status message.
            var st = r.status_json is string sj && JsonDocument.Parse(sj).RootElement is { ValueKind: JsonValueKind.Object } sr
                ? Payloads.Status(sr.Clone(), (string)r.serial, DateTimeOffset.UnixEpoch) : null;
            live.Load(new DeviceLive
            {
                LoggerIp = st?.Ip, LoggerRssi = st?.Rssi, LoggerSn = st?.LoggerSn, LoggerConn = st?.ConnType,
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
public sealed class BatchWriter(Db db, IngestPipeline pipeline, LiveStateCache live, BackfillTracker backfill, RollupTracker rollups, ILogger<BatchWriter> log) : BackgroundService
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
                    // Live rows: the next roll-up recomputes only these devices, from these times.
                    foreach (var t in telemetry)
                    {
                        if (t.SpanS is not null || t.Ts < late) backfill.Request(t.Serial, t.Ts);
                        else rollups.Mark(t.Serial, t.Ts);
                    }
                    foreach (var b in battery)
                    {
                        if (b.Ts < late) backfill.Request(b.Serial, b.Ts);
                        else rollups.Mark(b.Serial, b.Ts);
                    }
                    LastBatchMs = sw.Elapsed.TotalMilliseconds;
                    GgMetrics.BatchDuration.Record(LastBatchMs);
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
        await DrainOnShutdownAsync(telemetry, battery);
    }

    /// <summary>
    /// Shutdown: hosted services stop in reverse order, so MQTT no longer delivers; write what is still queued instead of
    /// dropping it (the broker already has its acknowledgement and will not send it again). Bounded to 10 s.
    /// </summary>
    private async Task DrainOnShutdownAsync(List<TelemetrySample> telemetry, List<BatterySample> battery)
    {
        try
        {
            using var budget = new CancellationTokenSource(TimeSpan.FromSeconds(10));
            var drained = 0;
            while (true)
            {
                while (telemetry.Count + battery.Count < 5000 && pipeline.Rows.TryRead(out var row))
                {
                    if (row is TelemetrySample t) telemetry.Add(t); else if (row is BatterySample b) battery.Add(b);
                }
                if (telemetry.Count + battery.Count == 0) break;
                await FlushAsync(telemetry, battery, budget.Token);
                drained += telemetry.Count + battery.Count;
                Written += telemetry.Count + battery.Count;
                telemetry.Clear();
                battery.Clear();
            }
            await FlushDevicesAsync(budget.Token);
            log.LogInformation("shutdown: wrote {Count} queued rows", drained);
        }
        catch (Exception ex) { log.LogError(ex, "shutdown: could not write {Count} queued rows", telemetry.Count + battery.Count); }
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
        var serials = new List<string>();
        var online = new List<bool>();
        var seen = new List<DateTimeOffset>();
        var cap = new List<double?>();
        var pv = new List<double>();
        var grid = new List<double>();
        foreach (var d in live.All)
        {
            lock (d.Gate)
            {
                if (!d.Dirty) continue;
                d.Dirty = false;
                serials.Add(d.Serial); online.Add(d.Online); seen.Add(d.LastSeen.ToUniversalTime());
                cap.Add(d.BatteryCapacityKwh); pv.Add(d.BattFromPvKwh); grid.Add(d.BattFromGridKwh);
            }
        }
        if (serials.Count == 0) return;
        await using var c = await db.OpenAsync(ct);
        // One statement for all devices: a list parameter would run the UPDATE once per device, and at 10.000 devices
        // that held this writer up for ~11 s of every 15 s, so raw samples were dropped (docs/LOADTEST.md, A7).
        await c.ExecuteAsync(new CommandDefinition(
            """
            UPDATE devices d SET online = u.online, last_seen = GREATEST(d.last_seen, u.seen),
                   battery_capacity_kwh = COALESCE(u.cap, d.battery_capacity_kwh), batt_from_pv_kwh = u.pv, batt_from_grid_kwh = u.grid
            FROM unnest(@serials::text[], @online::boolean[], @seen::timestamptz[], @cap::double precision[], @pv::double precision[], @grid::double precision[])
                 AS u(serial, online, seen, cap, pv, grid)
            WHERE d.serial = u.serial
            """,
            new { serials = serials.ToArray(), online = online.ToArray(), seen = seen.ToArray(), cap = cap.ToArray(), pv = pv.ToArray(), grid = grid.ToArray() },
            cancellationToken: ct));
    }
}

/// <summary>Aggregates raw samples into 15-minute and daily rows, and maintains partitions.</summary>
public sealed class RollupService(Db db, PartitionMaintenance partitions, BackfillTracker backfill, RollupTracker rollups, LiveStateCache live, IOptions<IngestOptions> options, TimeProvider clock, ILogger<RollupService> log) : BackgroundService
{
    // "/*serials*/" becomes a filter when only some devices are recalculated.
    private static string For(string sql, string column, bool filtered) => sql.Replace("/*serials*/", filtered ? $"AND {column} = ANY(@serials)" : "");

    private const string Rollup15 = Rollup15Samples + "\n" + Rollup15Upsert;

    // Samples of the window with the interval each one stands for (dt) and its PV / export / charge power.
    private const string Rollup15Samples =
        """
        WITH s AS (
            SELECT *, EXTRACT(EPOCH FROM ts - LAG(ts) OVER (PARTITION BY serial ORDER BY ts)) AS gap
            FROM telemetry_raw WHERE ts >= @from - @lag AND ts < @to /*serials*/
        ), g AS (
            -- Each sample stands for the time since the previous one. After a silence (or for the very first
            -- sample) that time is unknown, so only the nominal 5 s interval is counted, not the whole gap.
            -- A sample stored by the datalogger during an outage stands for its storage interval instead.
            SELECT *, CASE WHEN span_s IS NOT NULL THEN LEAST(span_s, COALESCE(gap, span_s))
                           WHEN gap IS NULL OR gap > 30 THEN 5 ELSE gap END AS dt FROM s
        ), e AS (
            SELECT *, GREATEST(pv_w, 0) AS pv, GREATEST(-grid_w, 0) AS exp, GREATEST(batt_w, 0) AS chg FROM g WHERE ts >= @from
        )
        """;

    // 15-minute rows of the window recomputed from the samples above.
    private const string Rollup15Upsert =
        """
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

    // Incremental run (plan A4): the same 15-minute upsert, and each day row moved by (new − old) of the buckets it
    // changed — instead of re-summing every bucket of the day, which costs a page read per bucket and grows all day long.
    // The average SOC is kept as a sum and a count for the same reason. The 6-hourly full pass recomputes everything.
    private const string Rollup15WithDailyDelta = Rollup15Samples + ", old AS MATERIALIZED (\n" +
        """
            SELECT serial, bucket, pv_kwh, load_kwh, import_kwh, export_kwh, charge_kwh, discharge_kwh, pv_self_kwh, pv_charge_kwh, pv_export_kwh, soc
            FROM rollup_15m WHERE bucket >= @from AND serial = ANY(@serials)
        ), up AS (
        """ + "\n" + Rollup15Upsert + "\n" +
        """
            RETURNING serial, bucket, pv_kwh, load_kwh, import_kwh, export_kwh, charge_kwh, discharge_kwh, pv_self_kwh, pv_charge_kwh, pv_export_kwh, soc
        ), d AS (
            SELECT u.serial, (u.bucket AT TIME ZONE COALESCE(p.time_zone, @tz))::date AS day,
                   sum(u.pv_kwh - COALESCE(o.pv_kwh, 0)) AS pv_kwh, sum(u.load_kwh - COALESCE(o.load_kwh, 0)) AS load_kwh,
                   sum(u.import_kwh - COALESCE(o.import_kwh, 0)) AS import_kwh, sum(u.export_kwh - COALESCE(o.export_kwh, 0)) AS export_kwh,
                   sum(u.charge_kwh - COALESCE(o.charge_kwh, 0)) AS charge_kwh, sum(u.discharge_kwh - COALESCE(o.discharge_kwh, 0)) AS discharge_kwh,
                   sum(u.pv_self_kwh - COALESCE(o.pv_self_kwh, 0)) AS pv_self_kwh, sum(u.pv_charge_kwh - COALESCE(o.pv_charge_kwh, 0)) AS pv_charge_kwh,
                   sum(u.pv_export_kwh - COALESCE(o.pv_export_kwh, 0)) AS pv_export_kwh,
                   sum(COALESCE(u.soc, 0) - COALESCE(o.soc, 0)) AS soc_sum,
                   sum((u.soc IS NOT NULL)::int - (o.soc IS NOT NULL)::int) AS soc_n
            FROM up u
            LEFT JOIN old o ON o.serial = u.serial AND o.bucket = u.bucket
            LEFT JOIN project_devices pd ON pd.serial = u.serial
            LEFT JOIN projects p ON p.id = pd.project_id
            GROUP BY 1, 2
        )
        INSERT INTO rollup_1d AS r1 (serial, day, pv_kwh, load_kwh, import_kwh, export_kwh, charge_kwh, discharge_kwh, pv_self_kwh, pv_charge_kwh, pv_export_kwh, soc_sum, soc_n, soc_avg)
        SELECT serial, day, pv_kwh, load_kwh, import_kwh, export_kwh, charge_kwh, discharge_kwh, pv_self_kwh, pv_charge_kwh, pv_export_kwh,
               soc_sum, soc_n, soc_sum / NULLIF(soc_n, 0)
        FROM d
        ON CONFLICT (serial, day) DO UPDATE SET
            pv_kwh = r1.pv_kwh + EXCLUDED.pv_kwh, load_kwh = r1.load_kwh + EXCLUDED.load_kwh,
            import_kwh = r1.import_kwh + EXCLUDED.import_kwh, export_kwh = r1.export_kwh + EXCLUDED.export_kwh,
            charge_kwh = r1.charge_kwh + EXCLUDED.charge_kwh, discharge_kwh = r1.discharge_kwh + EXCLUDED.discharge_kwh,
            pv_self_kwh = r1.pv_self_kwh + EXCLUDED.pv_self_kwh, pv_charge_kwh = r1.pv_charge_kwh + EXCLUDED.pv_charge_kwh,
            pv_export_kwh = r1.pv_export_kwh + EXCLUDED.pv_export_kwh,
            soc_sum = r1.soc_sum + EXCLUDED.soc_sum, soc_n = r1.soc_n + EXCLUDED.soc_n,
            soc_avg = (r1.soc_sum + EXCLUDED.soc_sum) / NULLIF(r1.soc_n + EXCLUDED.soc_n, 0)
        """;

    private const string RollupBattery =
        """
        UPDATE rollup_15m r SET batt_v = b.v, batt_a = b.a, batt_temp = b.t
        FROM (SELECT serial, date_bin('15 minutes', ts, TIMESTAMPTZ '2000-01-01') AS bucket, avg(voltage) AS v, avg(current_a) AS a, avg(temp_cells) AS t
              FROM battery_raw WHERE ts >= @from AND ts < @to /*serials*/ GROUP BY 1, 2) b
        WHERE r.serial = b.serial AND r.bucket = b.bucket
        """;

    /// <summary>How often the roll-up recomputes every device over the last days, as a safety net for the incremental runs.</summary>
    public static readonly TimeSpan FullPassEvery = TimeSpan.FromHours(6);
    private DateTimeOffset _lastFullPass = DateTimeOffset.MinValue;

    /// <summary>
    /// One roll-up run. Normally incremental: only devices with new live samples (<see cref="RollupTracker"/>), from the
    /// 15-minute bucket and the local day of their earliest new sample. A full pass (every device, last 20 min of samples,
    /// last days of daily rows) runs at start-up, every <see cref="FullPassEvery"/>, and when <paramref name="lookBack"/> is given.
    /// </summary>
    public async Task RunOnceAsync(DateTimeOffset now, CancellationToken ct, TimeSpan? lookBack = null)
    {
        await using var c = await db.OpenAsync(ct);
        var dirty = rollups.Take();
        try
        {
            if (lookBack is not null || now - _lastFullPass >= FullPassEvery)
            {
                // Daily rows are rebuilt from whole local days: start two days back at UTC midnight minus a day of margin.
                await RunAsync(c, Floor15(now - (lookBack ?? TimeSpan.FromMinutes(20))), now, new DateTimeOffset(now.UtcDateTime.Date.AddDays(-3), TimeSpan.Zero), null);
                await RefreshChargedTodayAsync(c, now, null);
                if (lookBack is null) _lastFullPass = now;
            }
            else if (dirty.Count > 0)
            {
                await RunIncrementalAsync(c, dirty, now);
                await RefreshChargedTodayAsync(c, now, dirty.Keys.ToArray());
            }
        }
        catch
        {
            rollups.Restore(dirty);   // try again on the next run
            throw;
        }

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

    /// <summary>
    /// Incremental run: 15-minute rows of the changed devices from the bucket of the earliest new sample; daily rows of
    /// each changed device from the local day of its own earliest new sample (normally just today).
    /// </summary>
    private async Task RunIncrementalAsync(NpgsqlConnection c, IReadOnlyDictionary<string, DateTimeOffset> dirty, DateTimeOffset now)
    {
        var serials = dirty.Keys.ToArray();
        // One minute before the window is enough to find each sample's predecessor: a gap over 30 s counts as 5 s anyway.
        // Only stored samples (span_s) need a longer look-back, and they go through the backfill pass with 15 minutes.
        var args = new { from = Floor15(dirty.Values.Min()), to = now.AddMinutes(1), serials, lag = TimeSpan.FromMinutes(1) };
        var t0 = System.Diagnostics.Stopwatch.GetTimestamp();
        await c.ExecuteAsync(new CommandDefinition(For(Rollup15WithDailyDelta, "serial", true),
            new { args.from, args.to, serials, args.lag, tz = options.Value.DefaultTimeZone }, commandTimeout: 300));
        var t1 = System.Diagnostics.Stopwatch.GetTimestamp();
        await c.ExecuteAsync(new CommandDefinition(For(RollupBattery, "serial", true), args, commandTimeout: 300));
        log.LogDebug("incremental rollup: 15m + daily {A} ms, battery {B} ms, {N} device(s)",
            System.Diagnostics.Stopwatch.GetElapsedTime(t0, t1).TotalMilliseconds, System.Diagnostics.Stopwatch.GetElapsedTime(t1).TotalMilliseconds, serials.Length);
    }

    private async Task RunAsync(NpgsqlConnection c, DateTimeOffset from, DateTimeOffset now, DateTimeOffset dayFrom, string[]? serials)
    {
        var filtered = serials is not null;
        var args = new { from, to = now.AddMinutes(1), serials = serials ?? [], lag = TimeSpan.FromMinutes(15) };
        await c.ExecuteAsync(new CommandDefinition(For(Rollup15, "serial", filtered), args, commandTimeout: 300));
        await c.ExecuteAsync(new CommandDefinition(For(RollupBattery, "serial", filtered), args, commandTimeout: 300));
        await c.ExecuteAsync(new CommandDefinition(For(RollupDaySimple, "r.serial", filtered),
            new { from = dayFrom, tz = options.Value.DefaultTimeZone, serials = serials ?? [] }, commandTimeout: 300));
    }

    // Rebuild the last local days completely (the first, possibly partial, day of the window is skipped).
    private const string RollupDaySimple =
        """
        INSERT INTO rollup_1d AS d (serial, day, pv_kwh, load_kwh, import_kwh, export_kwh, charge_kwh, discharge_kwh, pv_self_kwh, pv_charge_kwh, pv_export_kwh, soc_avg, soc_sum, soc_n)
        SELECT serial, day, sum(pv_kwh), sum(load_kwh), sum(import_kwh), sum(export_kwh), sum(charge_kwh), sum(discharge_kwh),
               sum(pv_self_kwh), sum(pv_charge_kwh), sum(pv_export_kwh), avg(soc), COALESCE(sum(soc), 0), count(soc)
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
            pv_self_kwh = EXCLUDED.pv_self_kwh, pv_charge_kwh = EXCLUDED.pv_charge_kwh, pv_export_kwh = EXCLUDED.pv_export_kwh, soc_avg = EXCLUDED.soc_avg, soc_sum = EXCLUDED.soc_sum, soc_n = EXCLUDED.soc_n
        """;

    // Today's battery charge by source for each device, "today" being the local day of the device's project.
    private const string ChargedTodaySql =
        """
        SELECT s.serial AS Serial, COALESCE(d.pv_charge_kwh, 0) AS Pv, COALESCE(d.charge_kwh, 0) AS Total
        FROM unnest(@serials::text[]) AS s(serial)
        LEFT JOIN project_devices pd ON pd.serial = s.serial
        LEFT JOIN projects p ON p.id = pd.project_id
        LEFT JOIN rollup_1d d ON d.serial = s.serial AND d.day = (@now AT TIME ZONE COALESCE(p.time_zone, @tz))::date
        """;

    /// <summary>
    /// Copies today's charged energy (PV part and the rest, from the grid) into the live state of the devices heard
    /// from in the last day, so the realtime frame carries it without a database read per message.
    /// </summary>
    private async Task RefreshChargedTodayAsync(NpgsqlConnection c, DateTimeOffset now, string[]? only)
    {
        // Incremental runs refresh only the devices they recomputed; a full pass refreshes everyone heard from today.
        var serials = only ?? live.All.Where(d => now - d.LastSeen < TimeSpan.FromDays(1)).Select(d => d.Serial).ToArray();
        if (serials.Length == 0) return;
        var rows = await c.QueryAsync<(string Serial, double Pv, double Total)>(
            ChargedTodaySql, new { serials, now, tz = options.Value.DefaultTimeZone });
        foreach (var r in rows)
        {
            if (live.Get(r.Serial) is not { } d) continue;
            lock (d.Gate) d.ChargedToday = new ChargedToday(r.Pv, Math.Max(r.Total - r.Pv, 0));
        }
    }

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
                var started = System.Diagnostics.Stopwatch.GetTimestamp();
                await RunOnceAsync(now, stop);
                var took = System.Diagnostics.Stopwatch.GetElapsedTime(started).TotalMilliseconds;
                GgMetrics.RollupDuration.Record(took);
                // Load tests turn this on (Logging:LogLevel:GgPower.Infrastructure.RollupService = Debug) to measure the job.
                log.LogDebug("rollup took {Ms} ms for {Devices} device(s)", took, live.All.Count());
            }
            catch (OperationCanceledException) when (stop.IsCancellationRequested) { break; }
            catch (Exception ex) { log.LogError(ex, "rollup failed"); }
            await Task.Delay(TimeSpan.FromSeconds(30), stop).ContinueWith(_ => { }, CancellationToken.None);
        }
    }
}
