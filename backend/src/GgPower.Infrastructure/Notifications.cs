using Dapper;
using GgPower.Domain;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace GgPower.Infrastructure;

/// <summary>Delivers a push notification to device tokens. The development implementation only logs.</summary>
public interface IPushSender
{
    Task SendAsync(IReadOnlyList<string> tokens, string kind, string title, string body, IReadOnlyDictionary<string, string> data, CancellationToken ct);
}

public sealed class LoggingPushSender(ILogger<LoggingPushSender> log) : IPushSender
{
    public Task SendAsync(IReadOnlyList<string> tokens, string kind, string title, string body, IReadOnlyDictionary<string, string> data, CancellationToken ct)
    {
        log.LogInformation("push ({Kind}) to {Count} device(s): {Title}; no push provider configured", kind, tokens.Count, title);
        return Task.CompletedTask;
    }
}

/// <summary>Device alert pushes and the daily report (20:00 project time), honouring each user's switches.</summary>
public sealed class NotificationService(Db db, IPushSender push, JobLeader leader, TimeProvider clock, ILogger<NotificationService> log)
    : BackgroundService, IDeviceEventSink
{
    public async Task OnAlarmAsync(AlarmEvent alarm, bool isNew, CancellationToken ct)
    {
        if (!isNew || !alarm.Triggered || alarm.Severity == "info") return;
        await using var c = await db.OpenAsync(ct);
        var tokens = (await c.QueryAsync<string>(
            """
            SELECT t.token FROM push_tokens t
            JOIN users u ON u.id = t.user_id AND u.status = 'active' AND u.notif_device_alerts
            JOIN project_members m ON m.user_id = u.id
            JOIN project_devices pd ON pd.project_id = m.project_id
            WHERE pd.serial = @serial
            """, new { serial = alarm.Serial })).ToList();
        if (tokens.Count == 0) return;
        await push.SendAsync(tokens, "device_alert", alarm.Title, alarm.Description,
            new Dictionary<string, string> { ["serial"] = alarm.Serial, ["code"] = alarm.Code, ["severity"] = alarm.Severity }, ct);
    }

    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        var sent = new Dictionary<Guid, DateOnly>();
        while (!stop.IsCancellationRequested)
        {
            try
            {
                if (await leader.TryLeadAsync(JobLeader.DailyReport, stop)) await DailyReportsAsync(sent, stop);
            }
            catch (OperationCanceledException) when (stop.IsCancellationRequested) { break; }
            catch (Exception ex) { log.LogError(ex, "daily report failed"); }
            await Task.Delay(TimeSpan.FromMinutes(1), stop).ContinueWith(_ => { }, CancellationToken.None);
        }
    }

    private async Task DailyReportsAsync(Dictionary<Guid, DateOnly> sent, CancellationToken ct)
    {
        var now = clock.GetUtcNow();
        await using var c = await db.OpenAsync(ct);
        var projects = await c.QueryAsync<(Guid Id, string Name, string TimeZone)>("SELECT id, name, time_zone FROM projects");
        foreach (var p in projects)
        {
            var local = TimeZoneInfo.ConvertTime(now, CommandService.Zone(p.TimeZone));
            var day = DateOnly.FromDateTime(local.DateTime);
            if (local.Hour != 20 || sent.GetValueOrDefault(p.Id) == day) continue;
            sent[p.Id] = day;
            // Recorded in the database too: a restart or another instance taking the job over within the hour must not send it again.
            if (await c.ExecuteAsync("INSERT INTO daily_reports_sent (project_id, day) VALUES (@id, @day) ON CONFLICT DO NOTHING", new { id = p.Id, day }) == 0) continue;
            var tokens = (await c.QueryAsync<string>(
                """
                SELECT t.token FROM push_tokens t
                JOIN users u ON u.id = t.user_id AND u.status = 'active' AND u.notif_daily_report
                JOIN project_members m ON m.user_id = u.id WHERE m.project_id = @id
                """, new { id = p.Id })).ToList();
            if (tokens.Count == 0) continue;
            var totals = await c.QuerySingleOrDefaultAsync<(double? Pv, double? Load)>(
                "SELECT sum(d.pv_kwh), sum(d.load_kwh) FROM rollup_1d d JOIN project_devices pd ON pd.serial = d.serial WHERE pd.project_id = @id AND d.day = @day",
                new { id = p.Id, day });
            await push.SendAsync(tokens, "daily_report", p.Name, "",
                new Dictionary<string, string>
                {
                    ["project_id"] = p.Id.ToString(), ["date"] = day.ToString("yyyy-MM-dd"),
                    ["pv_kwh"] = (totals.Pv ?? 0).ToString("0.0", System.Globalization.CultureInfo.InvariantCulture),
                    ["load_kwh"] = (totals.Load ?? 0).ToString("0.0", System.Globalization.CultureInfo.InvariantCulture),
                }, ct);
        }
    }
}

/// <summary>
/// Notices devices that went silent without a last-will message: pushes the "offline" state to
/// connected apps, and keeps one open "connection lost" alert per device while it lasts.
/// </summary>
public sealed class LiveMonitor(Db db, LiveStateCache live, IRealtimeBus bus, JobLeader leader, Microsoft.Extensions.Options.IOptions<IngestOptions> options, TimeProvider clock, ILogger<LiveMonitor> log) : BackgroundService
{
    public const string OfflineCode = "OFFLINE";
    private static readonly TimeSpan AlertAfter = TimeSpan.FromMinutes(5);

    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        var reachable = new Dictionary<string, bool>(StringComparer.OrdinalIgnoreCase);
        var alerted = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var started = clock.GetUtcNow();
        var leading = false;
        while (!stop.IsCancellationRequested)
        {
            try
            {
                var now = clock.GetUtcNow();
                // Right after a restart every device looks silent until its next message arrives.
                var settled = now - started > TimeSpan.FromMinutes(2);
                // Every instance pushes reachability to its own apps; only the job leader keeps the alerts.
                bool lead;
                try { lead = await leader.TryLeadAsync(JobLeader.OfflineAlerts, stop); }
                catch (Exception ex) when (!stop.IsCancellationRequested)
                {
                    log.LogWarning(ex, "could not check the offline-alerts job lock");
                    lead = false;
                }
                if (lead && !leading)
                {
                    // Taken over (or started): the alerts that are open now are whatever the previous leader left.
                    alerted.Clear();
                    await using var c = await db.OpenAsync(stop);
                    foreach (var s in await c.QueryAsync<string>("SELECT serial FROM alerts WHERE code = @c AND ended_at IS NULL", new { c = OfflineCode })) alerted.Add(s);
                }
                leading = lead;
                foreach (var d in live.All)
                {
                    var up = d.IsReachable(now);
                    if (reachable.TryGetValue(d.Serial, out var was) && was != up)
                        bus.Publish(d.Serial, LiveJson.Bytes(LiveJson.Snapshot(d, now)));
                    reachable[d.Serial] = up;

                    if (!lead) continue;
                    if (!up && settled && now - d.LastSeen > AlertAfter && alerted.Add(d.Serial)) await SetOfflineAlertAsync(d, true, now, stop);
                    else if (up && alerted.Remove(d.Serial)) await SetOfflineAlertAsync(d, false, now, stop);
                }
            }
            catch (OperationCanceledException) when (stop.IsCancellationRequested) { break; }
            catch (Exception ex) { log.LogError(ex, "live monitor failed"); }
            await Task.Delay(TimeSpan.FromSeconds(10), stop).ContinueWith(_ => { }, CancellationToken.None);
        }
    }

    private async Task SetOfflineAlertAsync(DeviceLive d, bool open, DateTimeOffset now, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        // Only devices that belong to a project raise alerts.
        if (!await c.ExecuteScalarAsync<bool>("SELECT EXISTS (SELECT 1 FROM project_devices WHERE serial = @s)", new { s = d.Serial })) return;
        if (open)
            // A link that keeps dropping continues the same alert instead of adding one per drop.
            await AlertStore.RaiseAsync(c, d.Serial, $"SYS-{d.LastSeen:yyyyMMddHHmmss}", OfflineCode, "warning", "Mất kết nối với thiết bị",
                "Hệ thống không nhận được dữ liệu từ thiết bị.", d.LastSeen, null, TimeSpan.FromMinutes(Math.Max(0, options.Value.AlertReopenMinutes)));
        else
            await c.ExecuteAsync("UPDATE alerts SET ended_at = @now WHERE serial = @serial AND code = @code AND ended_at IS NULL", new { now, serial = d.Serial, code = OfflineCode });
        await IngestPipeline.RecountAsync(c, d);
        bus.Publish(d.Serial, LiveJson.Bytes(new { type = "alerts_changed", serial = d.Serial }));
    }
}
