using System.Text.Json;
using System.Text.Json.Nodes;
using Dapper;
using GgPower.Domain;
using GgPower.Infrastructure;

namespace GgPower.Api.Endpoints;

public static class AlertEndpoints
{
    public sealed record ResolveRequest(bool? Resolved);

    private static string Local(DateTime utc, TimeZoneInfo zone) =>
        TimeZoneInfo.ConvertTimeFromUtc(DateTime.SpecifyKind(utc, DateTimeKind.Utc), zone).ToString("yyyy-MM-dd'T'HH:mm:ss");

    public static void Map(RouteGroupBuilder api)
    {
        var g = api.MapGroup("/projects/{projectId:guid}/alerts").RequireAuthorization();

        g.MapGet("", async (Guid projectId, HttpContext ctx, AccessService access, Db db, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            await using var c = await db.OpenAsync(ct);
            var rows = await c.QueryAsync(
                "SELECT id, severity, title, description, code, started_at, ended_at, resolved, occurrences, last_triggered_at FROM alerts WHERE serial = ANY(@serials) ORDER BY started_at DESC LIMIT 200",
                new { serials = a.Serials });
            return Api.Cached(ctx, rows.Select(r => new
            {
                id = (Guid)r.id, severity = (string)r.severity, title = (string)r.title, description = (string)r.description, code = (string)r.code,
                started_at = Local((DateTime)r.started_at, a.Zone), started_at_utc = (DateTime)r.started_at,
                ended_at_utc = (DateTime?)r.ended_at, resolved = (bool)r.resolved,
                // How many times the fault came back while this alert was not yet dealt with.
                occurrences = (int)r.occurrences, last_triggered_at_utc = (DateTime?)r.last_triggered_at,
            }));
        });

        g.MapGet("/{alertId:guid}", async (Guid projectId, Guid alertId, HttpContext ctx, AccessService access, Db db, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            await using var c = await db.OpenAsync(ct);
            var r = await c.QuerySingleOrDefaultAsync(
                """
                SELECT al.id, al.serial, al.severity, al.title, al.description, al.code, al.started_at, al.ended_at, al.resolved, al.occurrences, al.last_triggered_at, al.snapshot::text AS snapshot, pd.name AS device
                FROM alerts al JOIN project_devices pd ON pd.serial = al.serial
                WHERE al.id = @alertId AND al.serial = ANY(@serials)
                """, new { alertId, serials = a.Serials });
            if (r is null) return Api.NotFound("ALERT_NOT_FOUND");

            // Grid voltage around the event, one-minute steps (present while raw data is retained).
            DateTime started = r.started_at;
            var from = DateTime.SpecifyKind(started, DateTimeKind.Utc).AddMinutes(-15);
            var points = (await c.QueryAsync<(int Idx, double? V)>(
                "SELECT (floor(extract(epoch FROM ts - @from) / 60))::int, avg(grid_v) FROM telemetry_raw WHERE serial = @serial AND ts >= @from AND ts < @to GROUP BY 1",
                new { serial = (string)r.serial, from, to = from.AddMinutes(31) })).Where(p => p.V is not null).ToDictionary(p => p.Idx, p => p.V!.Value);
            object? chart = null;
            if ((string)r.code != LiveMonitor.OfflineCode && points.Count >= 5)
            {
                // Gaps are filled with the previous value so the series stays evenly spaced.
                var values = new List<double>();
                var lastV = points[points.Keys.Min()];
                for (var i = 0; i <= 30; i++) { if (points.TryGetValue(i, out var v)) lastV = v; values.Add(Math.Round(lastV, 1)); }
                chart = new { kind = "grid", unit = "volt", values, start = Local(from, a.Zone), step_seconds = 60, mark_index = 15 };
            }
            var knowledge = AlertKnowledge.For((string)r.code);
            return Api.Cached(ctx, new
            {
                id = (Guid)r.id, severity = (string)r.severity, title = (string)r.title, description = (string)r.description,
                device = (string)r.device, project_name = a.Project.Name,
                started_at = Local(started, a.Zone), ended_at = r.ended_at is DateTime e ? Local(e, a.Zone) : null,
                end_kind = r.ended_at is null ? null : "auto_recovered",
                resolved = (bool)r.resolved, code = (string)r.code, code_name = knowledge.Name,
                occurrences = (int)r.occurrences,
                last_triggered_at = r.last_triggered_at is DateTime lt ? Local(lt, a.Zone) : null,
                chart, causes = knowledge.Causes, steps = knowledge.Steps,
                snapshot = r.snapshot is string s ? JsonNode.Parse(s) : null,
            });
        });

        g.MapPut("/{alertId:guid}/resolved", async (Guid projectId, Guid alertId, ResolveRequest body, HttpContext ctx, AccessService access, Db db, LiveStateCache live, RealtimeHub hub, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            if (body.Resolved is not { } resolved) return Api.Invalid();
            await using var c = await db.OpenAsync(ct);
            var serial = await c.ExecuteScalarAsync<string?>(
                "UPDATE alerts SET resolved = @resolved, resolved_by = @by WHERE id = @alertId AND serial = ANY(@serials) RETURNING serial",
                new { resolved, by = resolved ? ctx.User.UserId() : (Guid?)null, alertId, serials = a.Serials });
            if (serial is null) return Api.NotFound("ALERT_NOT_FOUND");
            if (live.Get(serial) is { } d)
            {
                await IngestPipeline.RecountAsync(c, d);
                hub.Publish(serial, LiveJson.Bytes(new { type = "alerts_changed", serial }));
            }
            return Results.NoContent();
        });
    }
}

/// <summary>
/// Explanations per error code. The per-code texts are still to be written by the EMS team
/// (open question in the app repository); until then only the backend's own codes have content.
/// </summary>
public static class AlertKnowledge
{
    public sealed record Entry(string Name, string[] Causes, string[] Steps);

    private static readonly Dictionary<string, Entry> Known = new()
    {
        [LiveMonitor.OfflineCode] = new("Mất kết nối",
            ["Mất điện hoặc mất Wi-Fi tại nơi lắp đặt", "Bộ thu thập dữ liệu bị tắt hoặc ngoài vùng phủ sóng"],
            ["Kiểm tra nguồn điện và đèn trạng thái của bộ thu thập dữ liệu", "Kiểm tra mạng Wi-Fi", "Nếu vẫn mất kết nối, gửi yêu cầu hỗ trợ"]),
    };

    public static Entry For(string code) => Known.GetValueOrDefault(code) ?? new Entry(code, [], []);
}

public static class ConfigEndpoints
{
    public sealed record SwitchRequest(bool? Enabled, string? Reason);
    public sealed record AllowlistRequest(string? Note);

    private static string RoleOf(ProjectAccess a) => Roles.IsValid(a.Role) ? a.Role : Roles.Owner;

    public static void Map(RouteGroupBuilder api)
    {
        var g = api.MapGroup("/devices/{serial}").RequireAuthorization();

        g.MapGet("/config", async (string serial, HttpContext ctx, AccessService access, LiveStateCache live, CommandService commands, StatsService stats, TimeProvider clock, CancellationToken ct) =>
        {
            if (await access.DeviceAsync(ctx.User, serial, ct) is not { } a || live.Get(serial) is not { } d) return Api.NotFound("DEVICE_NOT_FOUND");
            var role = RoleOf(a);
            var tou = await commands.TouAsync(d.Serial, ct);
            var state = d.ConfigState;
            var mode = ConfigMapping.ReadMode(state, tou.TouActive);
            var backup = ConfigMapping.BackupSoc(state);
            var writeState = await commands.WriteStateAsync(d, ct);
            var local = TimeZoneInfo.ConvertTime(clock.GetUtcNow(), a.Zone);
            var strategies = CommandService.ParseStrategies(tou.Strategies);
            var today = mode == OperatingModes.Tou ? TouRules.ForDay(strategies, local.DayOfWeek) : null;
            var zero = state is { } s && s.TryGetProperty("zero_export", out var z) ? Payloads.Int(z, "limit_export_w") : null;
            var gridCharge = state is { } s2 && s2.TryGetProperty("charge_schedule", out var cs) ? Payloads.Int(cs, "charge_current_limit_a") : null;

            return Api.Cached(ctx, new
            {
                device_serial = d.Serial,
                version = ConfigMapping.Version(mode, backup, tou.Strategies, state),
                read_at = d.ConfigStateAt,
                mode,
                backup_soc_percent = backup,
                strategies = JsonNode.Parse(tou.Strategies),
                today_strategy_id = today?.Id,
                write_access = new
                {
                    state = writeState, role,
                    writable_groups = writeState == "enabled"
                        ? ConfigMapping.WritableGroups(state).Where(g => g != WriteGroups.BackupSoc || !d.BmsDisconnected).ToArray()
                        : [],
                    // A group the device cannot apply right now is reported as blocked rather than silently missing:
                    // firmware that cannot write the backup SOC, or no link to the battery's BMS.
                    blocked_groups = !ConfigMapping.SupportsBackupSoc(state)
                        ? new[] { new { group = WriteGroups.BackupSoc, reason = "NOT_SUPPORTED_BY_DEVICE" } }
                        : d.BmsDisconnected ? new[] { new { group = WriteGroups.BackupSoc, reason = "BMS_NOT_CONNECTED" } } : [],
                    backup_soc_range = new { min = ConfigMapping.BackupSocMin, max = ConfigMapping.BackupSocMax },
                    writable_modes = OperatingModes.WritableFor(role),
                },
                // Read-only parameters: only the ones the datalogger publishes; the rest are omitted (the app shows "—").
                settings = new
                {
                    battery_capacity_kwh = d.BatteryCapacityKwh,
                    average_load_kw_7d = await stats.AverageLoadKwAsync(d.Serial, ct),
                    grid_charge_current_a = gridCharge,
                    grid_work_mode_name = state is { } s3 ? Payloads.Str(s3, "grid_mode") : null,
                    zero_export_w = zero,
                },
            });
        });

        g.MapPost("/config-commands", async (string serial, JsonElement body, HttpContext ctx, AccessService access, LiveStateCache live, CommandService commands, AuditLog audit, Db db, CancellationToken ct) =>
        {
            if (await access.DeviceAsync(ctx.User, serial, ct) is not { } a || live.Get(serial) is not { } d) return Api.NotFound("DEVICE_NOT_FOUND");
            if (!Guid.TryParse(ctx.Request.Headers["Idempotency-Key"], out var key) || body.ValueKind != JsonValueKind.Object) return Api.Invalid();
            string? name;
            await using (var c = await db.OpenAsync(ct))
                name = await c.ExecuteScalarAsync<string?>("SELECT name FROM users WHERE id = @id", new { id = ctx.User.UserId() });
            var res = await commands.SubmitAsync(d, new Initiator(ctx.User.UserId(), name, RoleOf(a)), a.Project.TimeZone, body, key, ct);
            await audit.WriteAsync(ctx.User.UserId(), "config_command", d.Serial,
                new { group = Payloads.Str(body, "group"), result = res.ErrorCode ?? "accepted", command_id = res.Command?["command_id"]?.GetValue<string>() }, ctx.ClientIp(), ct);
            if (res.ErrorCode is not null)
            {
                if (res.Extra?.GetType().GetProperty("retry_after_seconds")?.GetValue(res.Extra) is int retry) ctx.Response.Headers.RetryAfter = retry.ToString();
                return Api.Error(res.Status, res.ErrorCode, res.Extra);
            }
            ctx.Response.Headers.Location = $"/api/v1/devices/{d.Serial}/config-commands/{res.Command!["command_id"]}";
            return Results.Json(res.Command, statusCode: 202);
        });

        g.MapGet("/config-commands", async (string serial, DateTimeOffset? from, DateTimeOffset? to, int? limit, string? cursor, HttpContext ctx, AccessService access, CommandService commands, CancellationToken ct) =>
        {
            if (await access.DeviceAsync(ctx.User, serial, ct) is null) return Api.NotFound("DEVICE_NOT_FOUND");
            DateTimeOffset? cur = DateTimeOffset.TryParse(cursor, null, System.Globalization.DateTimeStyles.AssumeUniversal, out var parsed) ? parsed.ToUniversalTime() : null;
            var (items, next) = await commands.ListAsync(serial, from?.ToUniversalTime(), to?.ToUniversalTime(), Math.Clamp(limit ?? 20, 1, 100), cur, ct);
            return Results.Json(new { items, next_cursor = next });
        });

        g.MapGet("/config-commands/{commandId:guid}", async (string serial, Guid commandId, HttpContext ctx, AccessService access, CommandService commands, CancellationToken ct) =>
        {
            if (await access.DeviceAsync(ctx.User, serial, ct) is null) return Api.NotFound("DEVICE_NOT_FOUND");
            return await commands.GetAsync(serial, commandId, ct) is { } cmd ? Results.Json(cmd) : Api.NotFound("COMMAND_NOT_FOUND");
        });

        // Emergency switch and the list of devices that may be written to. Staff with the write-admin flag only.
        var admin = api.MapGroup("/admin").RequireAuthorization("write_admin");

        admin.MapGet("/config-write-switch", async (Db db, CancellationToken ct) =>
        {
            await using var c = await db.OpenAsync(ct);
            var r = await c.QuerySingleAsync("SELECT value::text AS value, updated_at FROM app_settings WHERE key = 'config_write_switch'");
            var v = JsonNode.Parse((string)r.value)!.AsObject();
            v["changed_at"] = ((DateTime)r.updated_at).ToString("O");
            return Results.Json(v);
        });

        admin.MapPut("/config-write-switch", async (SwitchRequest body, HttpContext ctx, Db db, AuditLog audit, CancellationToken ct) =>
        {
            if (body.Enabled is not { } enabled || body.Reason is { Length: > 200 }) return Api.Invalid();
            var value = new JsonObject { ["enabled"] = enabled, ["reason"] = body.Reason, ["changed_by"] = ctx.User.UserId().ToString() };
            await using var c = await db.OpenAsync(ct);
            await c.ExecuteAsync("UPDATE app_settings SET value = @v::jsonb, updated_at = now() WHERE key = 'config_write_switch'", new { v = value.ToJsonString() });
            await audit.WriteAsync(ctx.User.UserId(), "config_write_switch", null, new { enabled, reason = body.Reason }, ctx.ClientIp(), ct);
            value["changed_at"] = DateTime.UtcNow.ToString("O");
            return Results.Json(value);
        });

        admin.MapGet("/config-write-allowlist", async (Db db, CancellationToken ct) =>
        {
            await using var c = await db.OpenAsync(ct);
            var rows = await c.QueryAsync("SELECT serial, note, added_by, added_at FROM config_allowlist ORDER BY added_at");
            return Results.Json(rows.Select(r => new { serial = (string)r.serial, note = (string?)r.note, added_by = (Guid?)r.added_by, added_at = (DateTime)r.added_at }));
        });

        admin.MapPut("/config-write-allowlist/{serial}", async (string serial, AllowlistRequest? body, HttpContext ctx, Db db, AuditLog audit, CancellationToken ct) =>
        {
            if (!UpTopic.IsSerial(serial) || body?.Note is { Length: > 200 }) return Api.Invalid();
            await using var c = await db.OpenAsync(ct);
            await c.ExecuteAsync(
                "INSERT INTO config_allowlist (serial, note, added_by) VALUES (@serial, @note, @by) ON CONFLICT (serial) DO UPDATE SET note = COALESCE(@note, config_allowlist.note)",
                new { serial, note = body?.Note, by = ctx.User.UserId() });
            await audit.WriteAsync(ctx.User.UserId(), "allowlist_add", serial, null, ctx.ClientIp(), ct);
            return Results.Json(new { serial, note = body?.Note });
        });

        admin.MapDelete("/config-write-allowlist/{serial}", async (string serial, HttpContext ctx, Db db, AuditLog audit, CancellationToken ct) =>
        {
            await using var c = await db.OpenAsync(ct);
            await c.ExecuteAsync("DELETE FROM config_allowlist WHERE upper(serial) = upper(@serial)", new { serial });
            await audit.WriteAsync(ctx.User.UserId(), "allowlist_remove", serial, null, ctx.ClientIp(), ct);
            return Results.NoContent();
        });
    }
}
