using System.Collections.Concurrent;
using Dapper;
using Microsoft.Extensions.Logging;
using Npgsql;

namespace GgPower.Infrastructure;

/// <summary>
/// Picks one instance to run each periodic job (plan B1). The leader holds a Postgres session advisory lock on a
/// dedicated, unpooled connection. When it stops, crashes or loses that connection, Postgres releases the lock and the
/// next instance that asks takes over, within one cycle of its job.
/// </summary>
public sealed class JobLeader(Db db, ILogger<JobLeader> log, InstanceRoles? roles = null) : IAsyncDisposable
{
    public const string Rollup = "rollup";              // roll-ups, partitions and table clean-up
    public const string DailyReport = "daily-report";
    public const string OfflineAlerts = "offline-alerts";
    public const string Commands = "commands";          // TOU reload at the new day and closing abandoned commands

    public static readonly IReadOnlyList<string> Jobs = [Rollup, DailyReport, OfflineAlerts, Commands];

    // First key of pg_try_advisory_lock(int, int), "GG"; the second is the job's position in Jobs.
    // The migrator uses the single-key form, so the two never collide.
    private const int LockSpace = 0x4747;

    private readonly ConcurrentDictionary<string, NpgsqlConnection> _held = new();
    private readonly SemaphoreSlim _gate = new(1, 1);

    public bool Holds(string job) => _held.ContainsKey(job);

    /// <summary>
    /// True when this instance leads <paramref name="job"/>. Jobs call it before every run: a leader checks that its
    /// session is still alive, a follower tries to take the lock.
    /// </summary>
    public async Task<bool> TryLeadAsync(string job, CancellationToken ct)
    {
        var key = IndexOf(job);
        // Without the jobs role this instance never takes a job (plan B7).
        if (roles is { RunsJobs: false }) return false;
        await _gate.WaitAsync(ct);
        try
        {
            if (_held.TryGetValue(job, out var held))
            {
                try
                {
                    await held.ExecuteScalarAsync<int>(new CommandDefinition("SELECT 1", cancellationToken: ct));
                    return true;
                }
                catch (Exception ex) when (!ct.IsCancellationRequested)
                {
                    log.LogWarning(ex, "lost the lock of the {Job} job", job);
                    _held.TryRemove(job, out _);
                    await CloseAsync(held);
                }
            }

            var c = await db.OpenDedicatedAsync(ct);
            try
            {
                if (await c.ExecuteScalarAsync<bool>(new CommandDefinition(
                        "SELECT pg_try_advisory_lock(@space, @key)", new { space = LockSpace, key }, cancellationToken: ct)))
                {
                    _held[job] = c;
                    log.LogInformation("this instance now runs the {Job} job", job);
                    return true;
                }
            }
            catch
            {
                await CloseAsync(c);
                throw;
            }
            await CloseAsync(c);
            return false;
        }
        finally { _gate.Release(); }
    }

    /// <summary>Gives the job up (the session ends, so another instance can take it).</summary>
    public async Task ReleaseAsync(string job)
    {
        await _gate.WaitAsync();
        try
        {
            if (_held.TryRemove(job, out var c)) await CloseAsync(c);
        }
        finally { _gate.Release(); }
    }

    public async ValueTask DisposeAsync()
    {
        foreach (var job in _held.Keys.ToList()) await ReleaseAsync(job);
    }

    private static int IndexOf(string job)
    {
        for (var i = 0; i < Jobs.Count; i++)
            if (Jobs[i] == job) return i + 1;
        throw new ArgumentException($"unknown job {job}", nameof(job));
    }

    private static async Task CloseAsync(NpgsqlConnection c)
    {
        try { await c.DisposeAsync(); }
        catch { /* the session is gone either way */ }
    }
}
