using System.Collections.Concurrent;
using System.Text.Json;
using System.Text.Json.Nodes;
using Dapper;
using GgPower.Domain;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace GgPower.Infrastructure;

public sealed class CommandOptions
{
    public int AckTimeoutSeconds { get; set; } = 10;
    public int ReadbackTimeoutSeconds { get; set; } = 8;
    public int ExpirySeconds { get; set; } = 30;
    public int MinIntervalSeconds { get; set; } = 5;
    public int OfflineAfterSeconds { get; set; } = 60;
}

public sealed record Initiator(Guid? UserId, string? Name, string? Role);

public sealed record SubmitResult(int Status, string? ErrorCode, object? Extra, JsonObject? Command)
{
    public static SubmitResult Fail(int status, string code, object? extra = null) => new(status, code, extra, null);
}

/// <summary>
/// Executes configuration changes on a device: publishes the datalogger commands, waits for each
/// acknowledgement, then confirms the result against the settings the inverter reports back. A change
/// is "applied" only after that read-back; otherwise the previous settings are restored.
/// </summary>
public sealed class CommandService(
    Db db, ILiveStore live, IServiceProvider services, JobLeader leader, ICommandCoordinator coord, IOptions<CommandOptions> options, IOptions<IngestOptions> ingest,
    TimeProvider clock, ILogger<CommandService> log) : BackgroundService, IDeviceEventSink
{
    private readonly CommandOptions _o = options.Value;
    private readonly ConcurrentDictionary<string, DateTimeOffset> _reloadTried = new(StringComparer.OrdinalIgnoreCase);
    private static readonly JsonSerializerOptions Json = LiveJson.Options;

    // Resolved lazily: the MQTT link depends on the ingest pipeline, which depends on this sink.
    private IMqttPublisher Mqtt => (IMqttPublisher)services.GetService(typeof(IMqttPublisher))!;

    public void OnAck(CommandAck ack) => coord.Deliver(ack);

    // Through the coordinator, so a command waiting on another instance sees the read-back too.
    public void OnConfigState(string serial, JsonElement state) => coord.DeliverConfig(serial, state, clock.GetUtcNow());

    // ---------- reading ----------

    public sealed class TouRow
    {
        public string Strategies { get; set; } = "[]";
        public bool TouActive { get; set; }
        public string? LoadedStrategyId { get; set; }
        public DateTime? LoadedOn { get; set; }
        public string? SavedGridMode { get; set; }
    }

    public async Task<TouRow> TouAsync(string serial, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        return await c.QuerySingleOrDefaultAsync<TouRow>(
            "SELECT strategies::text AS strategies, tou_active, loaded_strategy_id, loaded_on, saved_grid_mode FROM tou_strategies WHERE serial = @serial",
            new { serial }) ?? new TouRow();
    }

    public static List<TouStrategy> ParseStrategies(string json) =>
        JsonSerializer.Deserialize<List<TouStrategy>>(json, Json) ?? [];

    public async Task<bool> WriteEnabledAsync(CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        var v = await c.ExecuteScalarAsync<string?>("SELECT value::text FROM app_settings WHERE key = 'config_write_switch'");
        return v is null || JsonDocument.Parse(v).RootElement.TryGetProperty("enabled", out var e) && e.ValueKind == JsonValueKind.True;
    }

    public async Task<bool> AllowlistedAsync(string serial, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        return await c.ExecuteScalarAsync<bool>("SELECT EXISTS (SELECT 1 FROM config_allowlist WHERE upper(serial) = upper(@serial))", new { serial });
    }

    public bool GatewayOnline(DeviceLive d) => d.Online && clock.GetUtcNow() - d.LastSeen <= TimeSpan.FromSeconds(_o.OfflineAfterSeconds);

    /// <summary>enabled | device_read_only | disabled_by_server | gateway_offline</summary>
    public async Task<string> WriteStateAsync(DeviceLive d, CancellationToken ct) =>
        !await AllowlistedAsync(d.Serial, ct) ? "device_read_only"
        : !await WriteEnabledAsync(ct) ? "disabled_by_server"
        : !GatewayOnline(d) ? "gateway_offline"
        : "enabled";

    public static TimeZoneInfo Zone(string id)
    {
        try { return TimeZoneInfo.FindSystemTimeZoneById(id); }
        catch (Exception) { return TimeZoneInfo.FindSystemTimeZoneById("Asia/Ho_Chi_Minh"); }
    }

    // ---------- submitting ----------

    public async Task<SubmitResult> SubmitAsync(
        DeviceLive d, Initiator who, string timeZone, JsonElement request, Guid idempotencyKey, CancellationToken ct)
    {
        var group = Payloads.Str(request, "group");
        var basedOn = Payloads.Str(request, "based_on_version");
        if (group is not (WriteGroups.OperatingMode or WriteGroups.BackupSoc or WriteGroups.TouSchedule) || string.IsNullOrEmpty(basedOn))
            return SubmitResult.Fail(400, "VALIDATION_FAILED");
        var requestJson = request.GetRawText();
        var requestHash = Secrets.Sha256(requestJson);

        // A repeated request with the same key returns the command created the first time.
        await using (var c = await db.OpenAsync(ct))
        {
            var prior = await c.QuerySingleOrDefaultAsync<(Guid Id, byte[] RequestHash)>(
                "SELECT id, request_hash FROM config_commands WHERE user_id = @u AND idempotency_key = @k AND created_at > now() - interval '24 hours'",
                new { u = who.UserId, k = idempotencyKey });
            if (prior.Id != Guid.Empty)
                return prior.RequestHash.AsSpan().SequenceEqual(requestHash)
                    ? new SubmitResult(202, null, null, await GetAsync(d.Serial, prior.Id, ct))
                    : SubmitResult.Fail(422, "IDEMPOTENCY_KEY_REUSED");
        }

        var role = who.Role ?? Roles.Owner;
        var mode = group == WriteGroups.OperatingMode ? Payloads.Str(request, "mode") : null;
        if (group == WriteGroups.OperatingMode && mode is not null && OperatingModes.Writable.Contains(mode) && !OperatingModes.WritableFor(role).Contains(mode))
            return SubmitResult.Fail(403, "ROLE_NOT_PERMITTED");
        if (!ConfigMapping.WritableGroups(d.ConfigState).Contains(group)) return SubmitResult.Fail(403, "GROUP_NOT_IN_ALLOWLIST");
        if (!await AllowlistedAsync(d.Serial, ct)) return SubmitResult.Fail(409, "DEVICE_READ_ONLY");
        if (!await WriteEnabledAsync(ct)) return SubmitResult.Fail(409, "WRITE_DISABLED_BY_SERVER");
        if (!GatewayOnline(d) || !Mqtt.IsConnected || d.ConfigState is null) return SubmitResult.Fail(409, "GATEWAY_OFFLINE");
        if (group == WriteGroups.BackupSoc && d.BmsDisconnected) return SubmitResult.Fail(409, "BMS_NOT_CONNECTED");

        var tou = await TouAsync(d.Serial, ct);
        var strategies = ParseStrategies(tou.Strategies);
        var state = d.ConfigState;
        var currentMode = ConfigMapping.ReadMode(state, tou.TouActive);
        List<TouStrategy>? newStrategies = null;
        int? backupSoc = null;
        if (group == WriteGroups.OperatingMode)
        {
            if (mode is null || !OperatingModes.Writable.Contains(mode)) return SubmitResult.Fail(400, "VALIDATION_FAILED", new { errors = new[] { new FieldError("mode", "VALUE_INVALID") } });
        }
        else if (group == WriteGroups.BackupSoc)
        {
            backupSoc = Payloads.Int(request, "soc_percent");
            if (backupSoc is not (>= ConfigMapping.BackupSocMin and <= ConfigMapping.BackupSocMax))
                return SubmitResult.Fail(400, "VALIDATION_FAILED", new { errors = new[] { new FieldError("soc_percent", "VALUE_OUT_OF_RANGE") } });
        }
        else
        {
            try { newStrategies = request.TryGetProperty("strategies", out var s) ? s.Deserialize<List<TouStrategy>>(Json) : null; }
            catch (JsonException) { newStrategies = null; }
            if (newStrategies is null || newStrategies.Any(x => x is null || x.Weekdays is null || x.ChargeSlots is null || x.DischargeSlots is null || x.Id is null || x.Name is null
                    || x.ChargeSlots.Concat(x.DischargeSlots).Any(sl => sl?.Start is null || sl.End is null)))
                return SubmitResult.Fail(400, "VALIDATION_FAILED");
            var errors = TouRules.Validate(newStrategies);
            if (errors.Count > 0) return SubmitResult.Fail(400, "VALIDATION_FAILED", new { errors });
        }

        var version = ConfigMapping.Version(currentMode, ConfigMapping.BackupSoc(state), tou.Strategies, state);
        if (basedOn != version) return SubmitResult.Fail(409, "CONFIG_STALE", new { current_version = version });

        var now = clock.GetUtcNow();
        var local = TimeZoneInfo.ConvertTime(now, Zone(timeZone));
        // 00:00–00:01 belongs to the backend's own schedule reload.
        if (local is { Hour: 0, Minute: 0 }) return SubmitResult.Fail(409, "COMMAND_IN_PROGRESS");
        var minInterval = TimeSpan.FromSeconds(_o.MinIntervalSeconds);
        if (await coord.RetryAfterAsync(d.Serial, minInterval) is { } wait)
            return SubmitResult.Fail(429, "RATE_LIMITED", new { retry_after_seconds = (int)Math.Ceiling(wait.TotalSeconds) });

        var id = Guid.NewGuid();
        if (await coord.TryLockAsync(d.Serial, id, AbandonedAfter) is { } holder)
            return SubmitResult.Fail(409, "COMMAND_IN_PROGRESS", new { command_id = holder });
        await coord.StartIntervalAsync(d.Serial, minInterval);
        GgMetrics.CommandsSubmitted.Add(1, new KeyValuePair<string, object?>("group", group));

        try
        {
            await using var c = await db.OpenAsync(ct);
            await c.ExecuteAsync(
                """
                INSERT INTO config_commands (id, serial, user_id, user_name, user_role, grp, status, request, request_hash, idempotency_key, created_at, updated_at, expires_at)
                VALUES (@id, @serial, @u, @name, @role, @group, 'queued', @req::jsonb, @hash, @key, @now, @now, @exp)
                """,
                new { id, serial = d.Serial, u = who.UserId, name = who.Name, role = who.Role, group, req = requestJson, hash = requestHash, key = idempotencyKey, now, exp = now.AddSeconds(_o.ExpirySeconds) });
        }
        catch
        {
            await coord.UnlockAsync(d.Serial, id);
            throw;
        }

        var today = TouRules.ForDay(newStrategies ?? strategies, local.DayOfWeek);
        var localDay = DateOnly.FromDateTime(local.DateTime);
        _ = Task.Run(() => RunAsync(id, d, group, mode, currentMode, newStrategies, today, tou, localDay, now.AddSeconds(_o.ExpirySeconds), backupSoc));
        return new SubmitResult(202, null, null, await GetAsync(d.Serial, id, ct));
    }

    // ---------- executing ----------

    private async Task RunAsync(Guid id, DeviceLive d, string group, string? mode, string currentMode, List<TouStrategy>? newStrategies,
        TouStrategy? today, TouRow tou, DateOnly localDay, DateTimeOffset deadline, int? backupSoc = null)
    {
        var changes = new List<Change>();
        try
        {
            var before = d.ConfigState;
            List<DeviceCommand> plan;
            ExpectedState expected;
            var unsupported = new List<string>();

            if (group == WriteGroups.OperatingMode)
            {
                (plan, expected) = ConfigMapping.PlanMode(mode!, before, today, tou.SavedGridMode);
                changes.Add(new Change("operating_mode", currentMode, mode));
                if (mode == OperatingModes.Tou && today is { DischargeSlots.Count: > 0 } && !ConfigMapping.SupportsDischarge(before)) unsupported.Add("discharge_slots");
                // Timed charging only runs in TOU, so choosing another mode switches it off (and TOU switches it
                // on). That is a second setting changing on the inverter: record it, do not leave it implicit.
                var scheduleBefore = ConfigMapping.ScheduleEnabled(before);
                if (expected.ScheduleEnabled is { } scheduleAfter && scheduleAfter != scheduleBefore)
                    changes.Add(new Change("charge_schedule_enabled", scheduleBefore, scheduleAfter));
                var dischargeBefore = ConfigMapping.DischargeEnabled(before);
                if (expected.DischargeEnabled is { } dischargeAfter && dischargeAfter != dischargeBefore)
                    changes.Add(new Change("discharge_schedule_enabled", dischargeBefore, dischargeAfter));
            }
            else if (group == WriteGroups.BackupSoc)
            {
                (plan, expected) = ConfigMapping.PlanBackupSoc(backupSoc!.Value);
                changes.Add(new Change("backup_soc", ConfigMapping.BackupSoc(before), backupSoc));
            }
            else
            {
                var oldJson = tou.Strategies;
                var newJson = JsonSerializer.Serialize(newStrategies, Json);
                changes.Add(new Change("tou.strategies", JsonNode.Parse(oldJson), JsonNode.Parse(newJson)));
                // Discharge slots need a datalogger that reports (and accepts) the timed-discharge settings.
                if (newStrategies!.Any(s => s.DischargeSlots.Count > 0) && !ConfigMapping.SupportsDischarge(before)) unsupported.Add("discharge_slots");
                await SaveTouAsync(d.Serial, "strategies = @v::jsonb", new { v = newJson });
                if (currentMode != OperatingModes.Tou)
                {
                    // Not in TOU: the schedule is only stored and will be loaded when the user switches to TOU.
                    await FinishAsync(id, "applied", changes, new { device_written = false, device_state = "old_unchanged", unsupported }, null);
                    return;
                }
                (plan, expected) = ConfigMapping.PlanSchedule(before, today);
                if (plan.Count == 0)
                {
                    await SaveTouAsync(d.Serial, "loaded_strategy_id = @sid, loaded_on = @day", new { sid = today?.Id, day = localDay });
                    await FinishAsync(id, "applied", changes, new { device_written = false, device_state = "old_unchanged", unsupported }, null);
                    return;
                }
            }

            // The days the inverter runs its timed periods on change with the strategy that is loaded.
            if (plan.Any(c => c.Action == "SET_SCHEDULE_DAYS") && expected.ScheduleDays is { } newDays)
                changes.Add(new Change("schedule_days", ConfigMapping.ScheduleDays(before), newDays));

            await SetStatusAsync(id, "writing", changes);
            var sent = 0;
            var lastSent = clock.GetUtcNow();
            foreach (var cmd in plan)
            {
                lastSent = clock.GetUtcNow();
                var ack = await SendAsync(d, cmd, deadline);
                if (ack is not { Success: true })
                {
                    var failure = ack is null ? new { code = "ACK_TIMEOUT" }
                        : ack.Message.StartsWith("Biến tần từ chối", StringComparison.Ordinal) ? (object)new { code = "MODBUS_EXCEPTION" }
                        : new { code = "GATEWAY_ERROR", device_code = ack.Code };
                    // Nothing was written when the very first command was refused outright.
                    if (sent == 0 && ack is not null)
                    {
                        await FinishAsync(id, "failed", changes, new { device_written = false, device_state = "old_unchanged", unsupported }, failure);
                        return;
                    }
                    await RevertAsync(id, d, before, changes, failure, unsupported, deadline);
                    return;
                }
                sent++;
            }

            await SetStatusAsync(id, "verifying", changes);
            var after = await WaitConfigAsync(d, lastSent, deadline, expected);
            var ok = after is not null && ConfigMapping.Matches(expected, after);
            if (group == WriteGroups.OperatingMode)
            {
                var read = after is null ? null : ConfigMapping.ReadMode(after, mode == OperatingModes.Tou);
                changes[0].Readback = read;
                changes[0].ReadbackMatched = ok;
                if (changes.FirstOrDefault(x => x.Parameter == "charge_schedule_enabled") is { } sc && after is not null)
                {
                    var now = ConfigMapping.ScheduleEnabled(after);
                    sc.Readback = now;
                    sc.ReadbackMatched = now.Equals(sc.New);
                }
                if (changes.FirstOrDefault(x => x.Parameter == "discharge_schedule_enabled") is { } dc && after is not null)
                {
                    var now = ConfigMapping.DischargeEnabled(after);
                    dc.Readback = now;
                    dc.ReadbackMatched = now.Equals(dc.New);
                }
            }
            else if (group == WriteGroups.BackupSoc)
            {
                changes[0].Readback = ConfigMapping.BackupSoc(after);
                changes[0].ReadbackMatched = ok;
                // The level at which the battery is used again moves along with it: a second setting that changed.
                if (ConfigMapping.ResumeSoc(before) is { } resumeBefore && ConfigMapping.ResumeSoc(after) is { } resumeAfter && resumeBefore != resumeAfter)
                    changes.Add(new Change("battery_resume_soc", resumeBefore, resumeAfter) { Readback = resumeAfter, ReadbackMatched = true });
            }
            else changes[0].ReadbackMatched = ok;
            if (changes.FirstOrDefault(c => c.Parameter == "schedule_days") is { } days && ConfigMapping.ScheduleDays(after) is { } daysNow)
            {
                days.Readback = daysNow;
                days.ReadbackMatched = expected.ScheduleDays is { } want && want.SequenceEqual(daysNow);
            }

            if (!ok && group == WriteGroups.BackupSoc)
            {
                // One setting, nothing else to put back: report that the inverter may hold either value.
                await FinishAsync(id, "failed", changes, new { device_written = true, device_state = "unknown", unsupported },
                    new { code = after is null ? "ACK_TIMEOUT" : "READBACK_MISMATCH" });
                return;
            }
            if (!ok)
            {
                await RevertAsync(id, d, before, changes, new { code = after is null ? "ACK_TIMEOUT" : "READBACK_MISMATCH" }, unsupported, deadline);
                return;
            }

            if (group == WriteGroups.OperatingMode)
            {
                var enteringExport = mode == OperatingModes.MaxGridExport && currentMode != OperatingModes.MaxGridExport;
                await SaveTouAsync(d.Serial,
                    "tou_active = @active, loaded_strategy_id = @sid, loaded_on = @day" + (enteringExport ? ", saved_grid_mode = @grid" : ""),
                    new { active = mode == OperatingModes.Tou, sid = mode == OperatingModes.Tou ? today?.Id : null, day = localDay, grid = before is { } b ? Payloads.Str(b, "grid_mode") : null });
            }
            else if (group != WriteGroups.BackupSoc)
                await SaveTouAsync(d.Serial, "loaded_strategy_id = @sid, loaded_on = @day", new { sid = today?.Id, day = localDay });

            await FinishAsync(id, "applied", changes, new { device_written = true, device_state = "new", unsupported }, null);
        }
        catch (Exception ex)
        {
            log.LogError(ex, "config command {Id} crashed", id);
            try { await FinishAsync(id, "failed", changes, new { device_state = "unknown" }, new { code = "GATEWAY_ERROR" }); } catch (Exception) { }
        }
        finally
        {
            await coord.UnlockAsync(d.Serial, id);
        }
    }

    /// <summary>Puts back the settings read before the command, then reports whether that worked.</summary>
    private async Task RevertAsync(Guid id, DeviceLive d, JsonElement? before, List<Change> changes, object failure, List<string> unsupported, DateTimeOffset deadline)
    {
        await SetStatusAsync(id, "reverting", changes);
        var restored = false;
        if (before is { } old)
        {
            var (plan, previous) = ConfigMapping.PlanRestore(old);

            var extended = deadline.AddSeconds(_o.AckTimeoutSeconds * plan.Count);   // restoring gets its own budget
            var allAcked = true;
            var lastSent = clock.GetUtcNow();
            foreach (var cmd in plan)
            {
                lastSent = clock.GetUtcNow();
                if (await SendAsync(d, cmd, extended) is not { Success: true }) { allAcked = false; break; }
            }
            if (allAcked)
            {
                var after = await WaitConfigAsync(d, lastSent, extended, previous);
                restored = after is not null && ConfigMapping.Matches(previous, after);
            }
        }
        await FinishAsync(id, restored ? "reverted" : "failed", changes,
            new { device_written = true, device_state = restored ? "old_restored" : "unknown", unsupported }, failure);
    }

    private async Task<CommandAck?> SendAsync(DeviceLive d, DeviceCommand cmd, DateTimeOffset deadline)
    {
        var now = clock.GetUtcNow();
        var budget = TimeSpan.FromSeconds(Math.Min(_o.AckTimeoutSeconds, Math.Max(1, (deadline - now).TotalSeconds)));
        var cmdId = Guid.NewGuid().ToString("N");
        using var ack = coord.Expect(cmdId);
        try
        {
            var payload = new JsonObject { ["cmd_id"] = cmdId, ["action"] = cmd.Action, ["ts"] = now.ToUnixTimeSeconds(), ["params"] = cmd.Params.DeepClone() };
            await Mqtt.PublishAsync(UpTopic.Down(d.Tenant, d.Serial), payload.ToJsonString(), CancellationToken.None);
            var done = await Task.WhenAny(ack.Task, Task.Delay(budget));
            return done == ack.Task ? ack.Task.Result : null;
        }
        catch (Exception ex)
        {
            log.LogWarning("publishing {Action} to {Serial} failed: {Message}", cmd.Action, d.Serial, ex.Message);
            return null;
        }
    }

    /// <summary>
    /// Waits for the settings the inverter reports after the last command was sent to match
    /// <paramref name="expected"/>. The datalogger publishes its settings after every write, and with several
    /// commands the report of an earlier one can arrive late, so one non-matching report is not final:
    /// the latest report is returned when the time is up (null when none arrived at all).
    /// </summary>
    private async Task<JsonElement?> WaitConfigAsync(DeviceLive d, DateTimeOffset sentAt, DateTimeOffset deadline, ExpectedState expected)
    {
        var until = clock.GetUtcNow().AddSeconds(_o.ReadbackTimeoutSeconds);
        if (deadline.AddSeconds(_o.ReadbackTimeoutSeconds) < until) until = deadline.AddSeconds(_o.ReadbackTimeoutSeconds);
        JsonElement? latest = null;
        while (true)
        {
            if (coord.LatestConfig(d.Serial) is { } reported && reported.At >= sentAt)
            {
                latest = reported.State;
                if (ConfigMapping.Matches(expected, latest)) return latest;
            }
            if (clock.GetUtcNow() >= until) return latest;
            await Task.Delay(100);
        }
    }

    private async Task SaveTouAsync(string serial, string set, object args)
    {
        await using var c = await db.OpenAsync();
        var p = new DynamicParameters(args);
        p.Add("serial", serial);
        await c.ExecuteAsync("INSERT INTO tou_strategies (serial) VALUES (@serial) ON CONFLICT (serial) DO NOTHING", p);
        // `set` is one of the constant fragments in this file.
        await c.ExecuteAsync($"UPDATE tou_strategies SET {set}, updated_at = now() WHERE serial = @serial", p);
    }

    private async Task SetStatusAsync(Guid id, string status, List<Change> changes)
    {
        await using var c = await db.OpenAsync();
        await c.ExecuteAsync("UPDATE config_commands SET status = @status, changes = @ch::jsonb, updated_at = now() WHERE id = @id",
            new { id, status, ch = JsonSerializer.Serialize(changes, Json) });
    }

    private async Task FinishAsync(Guid id, string status, List<Change> changes, object outcome, object? failure)
    {
        await using var c = await db.OpenAsync();
        await c.ExecuteAsync(
            "UPDATE config_commands SET status = @status, changes = @ch::jsonb, outcome = @o::jsonb, failure = @f::jsonb, updated_at = now() WHERE id = @id",
            new { id, status, ch = JsonSerializer.Serialize(changes, Json), o = JsonSerializer.Serialize(outcome, Json), f = failure is null ? null : JsonSerializer.Serialize(failure, Json) });
        GgMetrics.CommandsFinished.Add(1, new KeyValuePair<string, object?>("status", status));
    }

    // ---------- queries ----------

    private const string Columns =
        "id, serial, user_id, user_name, user_role, grp, status, changes::text AS changes, outcome::text AS outcome, failure::text AS failure, created_at, updated_at, expires_at";

    private static JsonObject ToJson(dynamic r)
    {
        var final = (string)r.status is "applied" or "reverted" or "failed";
        var o = new JsonObject
        {
            ["command_id"] = ((Guid)r.id).ToString(),
            ["device_serial"] = (string)r.serial,
            ["group"] = (string)r.grp,
            ["status"] = (string)r.status,
            ["initiator"] = r.user_id is null
                ? new JsonObject { ["type"] = "system" }
                : new JsonObject { ["type"] = "user", ["user_id"] = ((Guid)r.user_id).ToString(), ["display_name"] = (string?)r.user_name, ["role"] = (string?)r.user_role },
            ["created_at"] = ((DateTime)r.created_at).ToString("O"),
            ["updated_at"] = ((DateTime)r.updated_at).ToString("O"),
            ["expires_at"] = ((DateTime)r.expires_at).ToString("O"),
        };
        if (!final) o["poll_after_ms"] = 1000;
        if (r.changes is string ch) o["changes"] = JsonNode.Parse(ch);
        if (r.outcome is string oc) o["outcome"] = JsonNode.Parse(oc);
        if (r.failure is string f) o["failure"] = JsonNode.Parse(f);
        return o;
    }

    public async Task<JsonObject?> GetAsync(string serial, Guid id, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        var r = await c.QuerySingleOrDefaultAsync($"SELECT {Columns} FROM config_commands WHERE id = @id AND upper(serial) = upper(@serial)", new { id, serial });
        return r is null ? null : ToJson(r);
    }

    public async Task<(List<JsonObject> Items, string? Next)> ListAsync(string serial, DateTimeOffset? from, DateTimeOffset? to, int limit, DateTimeOffset? cursor, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        var rows = (await c.QueryAsync(
            $"""
             SELECT {Columns} FROM config_commands
             WHERE upper(serial) = upper(@serial) AND created_at > now() - interval '90 days'
               AND (@from::timestamptz IS NULL OR created_at >= @from) AND (@to::timestamptz IS NULL OR created_at <= @to)
               AND (@cursor::timestamptz IS NULL OR created_at < @cursor)
             ORDER BY created_at DESC LIMIT @take
             """, new { serial, from, to, cursor, take = limit + 1 })).ToList();
        string? next = rows.Count > limit ? ((DateTime)rows[limit - 1].created_at).ToString("O") : null;
        return (rows.Take(limit).Select(r => (JsonObject)ToJson(r)).ToList(), next);
    }

    // ---------- background: abandoned commands and the daily schedule reload ----------

    /// <summary>Far longer than any command runs (expiry plus restoring), so only commands whose instance stopped are left.</summary>
    public static readonly TimeSpan AbandonedAfter = TimeSpan.FromMinutes(5);

    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        while (!stop.IsCancellationRequested)
        {
            try
            {
                if (await leader.TryLeadAsync(JobLeader.Commands, stop))
                {
                    await CloseAbandonedAsync(stop);
                    await ReloadSchedulesAsync(stop);
                }
            }
            catch (OperationCanceledException) when (stop.IsCancellationRequested) { break; }
            catch (Exception ex) { log.LogError(ex, "schedule reload failed"); }
            await Task.Delay(TimeSpan.FromSeconds(20), stop).ContinueWith(_ => { }, CancellationToken.None);
        }
    }

    /// <summary>
    /// Commands left unfinished by an instance that stopped can no longer be confirmed. Swept by age rather than at
    /// start-up, which would also close the commands other instances are still running.
    /// </summary>
    public async Task<int> CloseAbandonedAsync(CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        return await c.ExecuteAsync(
            """
            UPDATE config_commands SET status = 'failed', outcome = '{"device_state":"unknown"}', failure = '{"code":"ACK_TIMEOUT"}', updated_at = now()
            WHERE status NOT IN ('applied', 'reverted', 'failed') AND updated_at < @before
            """, new { before = clock.GetUtcNow() - AbandonedAfter });
    }

    /// <summary>
    /// The inverter holds a single set of slots. For devices in TOU mode, load the strategy of the new
    /// local day once the day has changed (and keep trying while the device is unreachable).
    /// </summary>
    public void ForgetReloadAttempts() => _reloadTried.Clear();

    public async Task ReloadSchedulesAsync(CancellationToken ct)
    {
        List<dynamic> rows;
        await using (var c = await db.OpenAsync(ct))
        {
            rows = (await c.QueryAsync(
                """
                SELECT t.serial, t.strategies::text AS strategies, t.loaded_strategy_id, t.loaded_on, COALESCE(p.time_zone, @tz) AS time_zone
                FROM tou_strategies t
                LEFT JOIN project_devices pd ON pd.serial = t.serial
                LEFT JOIN projects p ON p.id = pd.project_id
                WHERE t.tou_active
                """, new { tz = ingest.Value.DefaultTimeZone })).ToList();
        }
        var now = clock.GetUtcNow();
        foreach (var r in rows)
        {
            string serial = r.serial;
            var local = TimeZoneInfo.ConvertTime(now, Zone((string)r.time_zone));
            var day = DateOnly.FromDateTime(local.DateTime);
            var loadedOn = r.loaded_on is DateTime lo ? DateOnly.FromDateTime(lo) : (DateOnly?)null;
            if (loadedOn == day) continue;
            if (await live.GetAsync(serial, ct) is not { } d || !GatewayOnline(d) || !Mqtt.IsConnected || d.ConfigState is null) continue;
            if (!await AllowlistedAsync(serial, ct) || !await WriteEnabledAsync(ct)) continue;

            // A failed reload is retried at most every five minutes, not on every pass.
            if (_reloadTried.TryGetValue(serial, out var tried) && now - tried < TimeSpan.FromMinutes(5)) continue;
            _reloadTried[serial] = now;

            var today = TouRules.ForDay(ParseStrategies((string)r.strategies), local.DayOfWeek);
            var id = Guid.NewGuid();
            if (await coord.TryLockAsync(serial, id, AbandonedAfter) is not null) continue;
            try
            {
                var (plan, expected) = ConfigMapping.PlanSchedule(d.ConfigState, today);
                var changes = new List<Change> { new("tou.loaded_strategy", (string?)r.loaded_strategy_id, today?.Id) };
                await using (var c = await db.OpenAsync(ct))
                {
                    await c.ExecuteAsync(
                        """
                        INSERT INTO config_commands (id, serial, grp, status, request, request_hash, idempotency_key, created_at, updated_at, expires_at)
                        VALUES (@id, @serial, 'tou_schedule', 'writing', '{"reason":"daily_reload"}', @hash, @id, @now, @now, @exp)
                        """, new { id, serial, hash = Secrets.Sha256(id.ToString()), now, exp = now.AddSeconds(_o.ExpirySeconds) });
                }
                var deadline = now.AddSeconds(_o.ExpirySeconds);
                var ok = true;
                var lastSent = clock.GetUtcNow();
                foreach (var cmd in plan)
                {
                    lastSent = clock.GetUtcNow();
                    if (await SendAsync(d, cmd, deadline) is not { Success: true }) { ok = false; break; }
                }
                if (ok && plan.Count > 0)
                {
                    var after = await WaitConfigAsync(d, lastSent, deadline, expected);
                    ok = after is not null && ConfigMapping.Matches(expected, after);
                }
                if (ok)
                {
                    await SaveTouAsync(serial, "loaded_strategy_id = @sid, loaded_on = @day", new { sid = today?.Id, day });
                    await FinishAsync(id, "applied", changes, new { device_written = plan.Count > 0, device_state = plan.Count > 0 ? "new" : "old_unchanged" }, null);
                }
                else
                {
                    // Left for the next pass; the day stays "not loaded".
                    await FinishAsync(id, "failed", changes, new { device_state = "unknown" }, new { code = "ACK_TIMEOUT" });
                }
            }
            finally { await coord.UnlockAsync(serial, id); }
        }
    }
}
