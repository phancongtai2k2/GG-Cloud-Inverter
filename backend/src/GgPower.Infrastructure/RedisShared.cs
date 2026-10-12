using System.Collections.Concurrent;
using GgPower.Domain;
using StackExchange.Redis;

namespace GgPower.Infrastructure;

// Redis versions of the shared state (plan B4), used when Shared:Provider = redis so that every API instance sees the
// same tickets, command locks and cache versions. Keys are "<prefix><kind>:<value>"; serials are upper-cased since
// the in-memory versions compare them without case.

public sealed class SharedOptions
{
    /// <summary>memory (one instance) or redis.</summary>
    public string Provider { get; set; } = "memory";

    /// <summary>StackExchange.Redis configuration string, e.g. "redis:6379".</summary>
    public string Redis { get; set; } = "localhost:6379";

    /// <summary>Put before every key, so several environments can share one Redis.</summary>
    public string KeyPrefix { get; set; } = "ggp:";
}

public sealed class RedisKeys(string prefix)
{
    public RedisKey Ticket(string ticket) => $"{prefix}ticket:{ticket}";
    public RedisKey CommandLock(string serial) => $"{prefix}cmdlock:{serial.ToUpperInvariant()}";
    public RedisKey CommandInterval(string serial) => $"{prefix}cmdlast:{serial.ToUpperInvariant()}";
    public RedisKey CacheVersion(string serial) => $"{prefix}cachever:{serial.ToUpperInvariant()}";
    public RedisChannel Channel(string name) => RedisChannel.Literal($"{prefix}{name}");
}

public sealed class RedisTicketStore(IConnectionMultiplexer redis, RedisKeys keys) : ITicketStore
{
    public async Task<string> IssueAsync(Guid userId, string role, TimeSpan lifetime)
    {
        var ticket = Secrets.NewToken();
        await redis.GetDatabase().StringSetAsync(keys.Ticket(ticket), $"{userId:N}|{role}", lifetime);
        return ticket;
    }

    public async Task<(Guid UserId, string Role)?> RedeemAsync(string? ticket)
    {
        if (string.IsNullOrEmpty(ticket) || ticket.Length > 128) return null;
        // GETDEL: a ticket works once, on whichever instance the WebSocket reaches.
        var v = (string?)await redis.GetDatabase().StringGetDeleteAsync(keys.Ticket(ticket));
        if (v is null || v.Split('|', 2) is not [var id, var role] || !Guid.TryParse(id, out var userId)) return null;
        return (userId, role);
    }
}

public sealed class RedisCommandCoordinator(IConnectionMultiplexer redis, RedisKeys keys) : ICommandCoordinator
{
    // Deletes the lock only when it still belongs to this command (it may have expired and been taken since).
    private const string UnlockScript = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private readonly ConcurrentDictionary<string, TaskCompletionSource<CommandAck>> _acks = new();

    public async Task<TimeSpan?> RetryAfterAsync(string serial, TimeSpan minInterval)
    {
        var ttl = await redis.GetDatabase().KeyTimeToLiveAsync(keys.CommandInterval(serial));
        return ttl is { } t && t > TimeSpan.Zero ? t : null;
    }

    public async Task StartIntervalAsync(string serial, TimeSpan minInterval)
    {
        if (minInterval > TimeSpan.Zero) await redis.GetDatabase().StringSetAsync(keys.CommandInterval(serial), "1", minInterval);
    }

    public async Task<Guid?> TryLockAsync(string serial, Guid id, TimeSpan ttl)
    {
        var db = redis.GetDatabase();
        var key = keys.CommandLock(serial);
        for (var attempt = 0; ; attempt++)
        {
            if (await db.StringSetAsync(key, id.ToString("N"), ttl, When.NotExists)) return null;
            if (Guid.TryParse((string?)await db.StringGetAsync(key), out var holder)) return holder;
            if (attempt == 1) return Guid.Empty;   // freed and taken again twice in a row: report it as busy
        }
    }

    public Task UnlockAsync(string serial, Guid id) =>
        redis.GetDatabase().ScriptEvaluateAsync(UnlockScript, [keys.CommandLock(serial)], [id.ToString("N")]);

    // Acknowledgements still go to the waiter on this instance; B5 carries them between instances.
    public AckWait Expect(string cmdId)
    {
        var tcs = new TaskCompletionSource<CommandAck>(TaskCreationOptions.RunContinuationsAsynchronously);
        _acks[cmdId] = tcs;
        return new AckWait(tcs.Task, () => _acks.TryRemove(cmdId, out _));
    }

    public void Deliver(CommandAck ack)
    {
        if (_acks.TryRemove(ack.CmdId, out var tcs)) tcs.TrySetResult(ack);
    }
}

public sealed class RedisCacheVersions(IConnectionMultiplexer redis, RedisKeys keys) : ICacheVersions
{
    public async Task<long> VersionAsync(string? serial) =>
        serial is null ? 0 : (long?)await redis.GetDatabase().StringGetAsync(keys.CacheVersion(serial)) ?? 0;

    public async Task BumpAsync(IReadOnlyCollection<string> serials)
    {
        var db = redis.GetDatabase();
        await Task.WhenAll(serials.Select(s => db.StringIncrementAsync(keys.CacheVersion(s))));
    }
}
