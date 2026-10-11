using System.Collections.Concurrent;
using System.Net.WebSockets;
using System.Text.Json;
using Dapper;
using GgPower.Domain;
using GgPower.Infrastructure;

namespace GgPower.Api.Endpoints;

public static class DataEndpoints
{
    private static double? Kw(double? w) => w is { } v ? Math.Round(v / 1000, 3) : null;

    public static void Map(RouteGroupBuilder api)
    {
        var g = api.MapGroup("/projects/{projectId:guid}").RequireAuthorization();

        // Home screen, first paint. Afterwards the app keeps it fresh from the realtime channel.
        g.MapGet("/overview", async (Guid projectId, HttpContext ctx, AccessService access, LiveStateCache live, StatsService stats, Db db, TimeProvider clock, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            var now = clock.GetUtcNow();
            var d = a.PrimarySerial is { } serial ? live.Get(serial) : null;
            var reachable = d?.IsReachable(now) == true;
            var t = reachable ? d!.Telemetry : null;
            // Whether there is a battery is the inverter's statement (null: not reported), never derived here.
            var present = d?.BatteryPresent;
            var today = stats.Today(a.Zone);
            var touActive = false;
            if (d is not null)
            {
                await using var c = await db.OpenAsync(ct);
                touActive = await c.ExecuteScalarAsync<bool?>("SELECT tou_active FROM tou_strategies WHERE serial = @s", new { s = d.Serial }) ?? false;
            }
            var yieldToday = t?.YieldToday ?? (d is null ? 0 : await stats.DayYieldAsync(d.Serial, today, ct));
            return Results.Json(new
            {
                project_name = a.Project.Name,
                serial = d?.Serial,
                model = d?.Model,
                status = d is null ? "waiting_connection" : d.StatusName(now),
                updated_at = d?.LastSeen,
                unresolved_alerts = d?.Unresolved ?? 0,
                yield_today_kwh = yieldToday,
                // Same emission factor as /stats summary.co2_kg; null before the device is bound.
                co2_today_kg = d is null ? null : LiveJson.Co2Kg(yieldToday),
                // kWh charged into the battery since local midnight, by source; null without a device or without a battery.
                battery_charged_today = d is null || present == false ? null : LiveJson.Charged(await stats.DayChargeAsync(d.Serial, today, ct)),
                yield_yesterday_kwh = d is null ? 0 : await stats.DayYieldAsync(d.Serial, today.AddDays(-1), ct),
                energy = t is null ? null : new
                {
                    pv_kw = Kw(t.PvW), grid_kw = Kw(t.GridW), battery_kw = present == false ? null : Kw(t.BattW), load_kw = Kw(t.LoadW),
                    battery_soc_percent = present != false && t.Soc is { } s ? (int?)Math.Round(s) : null,
                },
                battery_present = present,
                mode = ConfigMapping.ReadMode(d?.ConfigState, touActive),
                backup_soc_percent = ConfigMapping.BackupSoc(d?.ConfigState),
                battery = d?.BatteryCapacityKwh is > 0 && present != false ? new
                {
                    capacity_kwh = d.BatteryCapacityKwh, from_pv_kwh = Math.Round(d.BattFromPvKwh, 2), from_grid_kwh = Math.Round(d.BattFromGridKwh, 2),
                } : null,
            });
        });

        g.MapGet("/day-curves", async (Guid projectId, string? date, HttpContext ctx, AccessService access, StatsService stats, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            if (!DateOnly.TryParseExact(date, "yyyy-MM-dd", out var day)) return Api.Invalid();
            var (body, closed) = await stats.DayCurvesAsync(a, day, ct);
            return Api.Cached(ctx, body, immutable: closed, maxAgeSeconds: 60);
        });

        // Energy buckets, yield buckets and the period summary in one response (one round trip per period).
        g.MapGet("/stats", async (Guid projectId, string? period, string? anchor, HttpContext ctx, AccessService access, StatsService stats, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            if (period is not ("day" or "month" or "year") || !DateOnly.TryParseExact(anchor, "yyyy-MM-dd", out var day)) return Api.Invalid();
            var (body, closed) = await stats.StatsAsync(a, period, day, ct);
            return Api.Cached(ctx, body, immutable: closed, maxAgeSeconds: 60);
        });

        g.MapGet("/devices", async (Guid projectId, HttpContext ctx, AccessService access, LiveStateCache live, Db db, TimeProvider clock, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            var now = clock.GetUtcNow();
            await using var c = await db.OpenAsync(ct);
            var names = (await c.QueryAsync<(string Serial, string Name)>("SELECT serial, name FROM project_devices WHERE project_id = @projectId", new { projectId }))
                .ToDictionary(x => x.Serial, x => x.Name, StringComparer.OrdinalIgnoreCase);
            return Results.Json(a.Serials.Select(serial =>
            {
                var d = live.Get(serial);
                var reachable = d?.IsReachable(now) == true;
                var t = reachable ? d!.Telemetry : null;
                var b = reachable ? d!.Battery : null;
                // A battery is listed when the inverter reports one. A datalogger that does not report it
                // (older firmware) leaves the question open, and its battery data is passed on as it is.
                var present = d?.BatteryPresent;
                var hasBattery = d?.Battery is not null && present != false;
                return new
                {
                    inverter = new
                    {
                        name = names.GetValueOrDefault(serial, d?.Model ?? "Inverter"), serial,
                        status = d is null ? "waiting_connection" : d.StatusName(now),
                        power_kw = Kw(t?.PvW), temperature_c = t?.InvTemp, firmware = d?.InverterFw, logger_firmware = d?.LoggerFw,
                    },
                    battery_present = present,
                    bms_comm = d?.Battery?.BmsComm,
                    datalogger = d is null ? null : LiveJson.Datalogger(d, reachable),
                    battery = !hasBattery ? null : new
                    {
                        name = d!.Battery!.Model ?? "Battery",
                        state = b?.CurrentA is not { } cur ? "idle" : cur > 0.5 ? "charging" : cur < -0.5 ? "discharging" : "idle",
                        hours_remaining = b?.EstMinutes is { } m ? (double?)Math.Round(m / 60, 1) : null,
                        soc_percent = b?.Soc is { } s ? (int?)Math.Round(s) : null,
                        power_kw = b is { CurrentA: { } ca, Voltage: { } v } ? (double?)Math.Round(ca * v / 1000, 2) : null,
                        voltage_v = b?.Voltage,
                        soh_percent = b?.Soh is { } h ? (int?)Math.Round(h) : null,
                    },
                };
            }));
        });

        // Full live snapshot of the project's device (device detail screens, first paint).
        g.MapGet("/telemetry", async (Guid projectId, HttpContext ctx, AccessService access, LiveStateCache live, TimeProvider clock, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            if (a.PrimarySerial is not { } serial || live.Get(serial) is not { } d) return Api.NotFound("DEVICE_NOT_FOUND");
            return Results.Json(LiveJson.Snapshot(d, clock.GetUtcNow()), LiveJson.Options);
        });

        g.MapGet("/history", async (Guid projectId, string? range, string? fields, HttpContext ctx, AccessService access, StatsService stats, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            var ids = (fields ?? "").Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
            // The app loads every field of a device in one call and lets the user pick up to three to draw.
            if (range is not ("hour" or "day" or "week") || ids.Length is 0 or > 12 || !ids.All(StatsService.KnownField)) return Api.Invalid();
            return Api.Cached(ctx, await stats.HistoryAsync(a, range, ids, ct), maxAgeSeconds: range == "hour" ? 20 : 120);
        });
    }
}

/// <summary>One-time tickets that authenticate a WebSocket, so the access token never appears in a URL.</summary>
public sealed class RealtimeTickets(TimeProvider clock)
{
    private readonly ConcurrentDictionary<string, (Guid UserId, string Role, DateTimeOffset Expires)> _tickets = new();
    public static readonly TimeSpan Lifetime = TimeSpan.FromSeconds(30);

    public string Issue(Guid userId, string role)
    {
        var now = clock.GetUtcNow();
        foreach (var kv in _tickets) if (kv.Value.Expires < now) _tickets.TryRemove(kv.Key, out _);
        var ticket = Secrets.NewToken();
        _tickets[ticket] = (userId, role, now + Lifetime);
        return ticket;
    }

    public (Guid UserId, string Role)? Redeem(string? ticket)
    {
        if (string.IsNullOrEmpty(ticket) || !_tickets.TryRemove(ticket, out var t) || t.Expires < clock.GetUtcNow()) return null;
        return (t.UserId, t.Role);
    }
}

public static class RealtimeEndpoints
{
    private const int MaxSubscriptions = 20;

    public static void Map(WebApplication app, RouteGroupBuilder api)
    {
        api.MapPost("/realtime/ticket", (HttpContext ctx, RealtimeTickets tickets) =>
            Results.Json(new { ticket = tickets.Issue(ctx.User.UserId(), ctx.User.Role()), expires_in = (int)RealtimeTickets.Lifetime.TotalSeconds }))
            .RequireAuthorization();

        // Protocol: client sends {"subscribe":["<project id>", ...]}; the server answers with a snapshot of
        // each device at once and then pushes every change. {"ping":1} is answered with {"type":"pong"}.
        app.Map("/ws/v1/realtime", async (HttpContext ctx, RealtimeTickets tickets, RealtimeHub hub, LiveStateCache live, Db db, TimeProvider clock, ILoggerFactory lf) =>
        {
            if (!ctx.WebSockets.IsWebSocketRequest) { ctx.Response.StatusCode = 400; return; }
            if (tickets.Redeem(ctx.Request.Query["ticket"]) is not { } who) { ctx.Response.StatusCode = 401; return; }

            using var ws = await ctx.WebSockets.AcceptWebSocketAsync();
            using var sub = hub.Connect();
            using var cts = CancellationTokenSource.CreateLinkedTokenSource(ctx.RequestAborted);
            var ct = cts.Token;

            var sender = Task.Run(async () =>
            {
                try
                {
                    await foreach (var msg in sub.Reader.ReadAllAsync(ct))
                        await ws.SendAsync(msg, WebSocketMessageType.Text, true, ct);
                }
                catch (Exception) { /* connection gone */ }
                finally { cts.Cancel(); }
            }, ct);

            var buffer = new byte[4096];
            try
            {
                while (ws.State == WebSocketState.Open)
                {
                    var res = await ws.ReceiveAsync(buffer, ct);
                    if (res.MessageType == WebSocketMessageType.Close) break;
                    if (!res.EndOfMessage || res.MessageType != WebSocketMessageType.Text) break;   // oversized or binary: drop the client
                    JsonDocument doc;
                    try { doc = JsonDocument.Parse(buffer.AsMemory(0, res.Count)); } catch (JsonException) { break; }
                    using (doc)
                    {
                        var root = doc.RootElement;
                        if (root.ValueKind != JsonValueKind.Object) break;
                        if (root.TryGetProperty("ping", out _)) { sub.Push(LiveJson.Bytes(new { type = "pong" })); continue; }
                        if (!root.TryGetProperty("subscribe", out var arr) || arr.ValueKind != JsonValueKind.Array) continue;
                        var ids = arr.EnumerateArray().Take(MaxSubscriptions)
                            .Select(e => e.ValueKind == JsonValueKind.String && Guid.TryParse(e.GetString(), out var id) ? id : Guid.Empty)
                            .Where(id => id != Guid.Empty).Distinct().ToArray();
                        // Only devices of projects the ticket's user is a member of.
                        await using var c = await db.OpenAsync(ct);
                        var serials = (await c.QueryAsync<string>(
                            "SELECT pd.serial FROM project_devices pd JOIN project_members m ON m.project_id = pd.project_id WHERE m.user_id = @user AND pd.project_id = ANY(@ids)",
                            new { user = who.UserId, ids })).ToArray();
                        hub.SetSerials(sub, serials);
                        var now = clock.GetUtcNow();
                        sub.Push(LiveJson.Bytes(new { type = "subscribed", serials }));
                        foreach (var s in serials)
                            if (live.Get(s) is { } d) sub.Push(LiveJson.Bytes(LiveJson.Snapshot(d, now)));
                    }
                }
            }
            catch (OperationCanceledException) { }
            catch (WebSocketException) { }
            finally
            {
                cts.Cancel();
                if (ws.State is WebSocketState.Open or WebSocketState.CloseReceived)
                {
                    try { await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, null, CancellationToken.None); } catch (Exception) { }
                }
            }
        }).DisableRateLimiting();
    }
}
