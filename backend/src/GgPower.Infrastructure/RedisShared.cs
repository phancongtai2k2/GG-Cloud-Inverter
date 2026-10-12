using System.Collections.Concurrent;
using System.Text;
using System.Text.Json;
using GgPower.Domain;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
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
    public RedisKey Device(string serial) => $"{prefix}dev:{serial.ToUpperInvariant()}";
    public RedisKey Watch => $"{prefix}rtwatch";
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

public sealed class RedisCommandCoordinator(IConnectionMultiplexer redis, RedisKeys keys, ILogger<RedisCommandCoordinator> log) : ICommandCoordinator, IRedisSubscriber
{
    // Deletes the lock only when it still belongs to this command (it may have expired and been taken since).
    private const string UnlockScript = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private sealed record ConfigMessage(string Serial, DateTimeOffset At, JsonElement State);

    private readonly ConcurrentDictionary<string, TaskCompletionSource<CommandAck>> _acks = new();
    // Devices this instance runs a command for, and the settings they reported meanwhile.
    private readonly ConcurrentDictionary<string, Guid> _local = new(StringComparer.OrdinalIgnoreCase);
    private readonly ConcurrentDictionary<string, (JsonElement State, DateTimeOffset At)> _configs = new(StringComparer.OrdinalIgnoreCase);

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
            if (await db.StringSetAsync(key, id.ToString("N"), ttl, When.NotExists))
            {
                _local[serial] = id;
                return null;
            }
            if (Guid.TryParse((string?)await db.StringGetAsync(key), out var holder)) return holder;
            if (attempt == 1) return Guid.Empty;   // freed and taken again twice in a row: report it as busy
        }
    }

    public async Task UnlockAsync(string serial, Guid id)
    {
        if (_local.TryRemove(new KeyValuePair<string, Guid>(serial, id))) _configs.TryRemove(serial, out _);
        await redis.GetDatabase().ScriptEvaluateAsync(UnlockScript, [keys.CommandLock(serial)], [id.ToString("N")]);
    }

    public AckWait Expect(string cmdId)
    {
        var tcs = new TaskCompletionSource<CommandAck>(TaskCreationOptions.RunContinuationsAsynchronously);
        _acks[cmdId] = tcs;
        return new AckWait(tcs.Task, () => _acks.TryRemove(cmdId, out _));
    }

    // The instance that receives device data publishes; every instance (this one too) hands it to its own waiter.
    public void Deliver(CommandAck ack) => Publish(keys.Channel("ack"), JsonSerializer.SerializeToUtf8Bytes(ack));

    public void DeliverConfig(string serial, JsonElement state, DateTimeOffset at) =>
        Publish(keys.Channel("cfg"), JsonSerializer.SerializeToUtf8Bytes(new ConfigMessage(serial, at, state)));

    public (JsonElement State, DateTimeOffset At)? LatestConfig(string serial) => _configs.TryGetValue(serial, out var c) ? c : null;

    public async Task SubscribeAsync()
    {
        var sub = redis.GetSubscriber();
        (await sub.SubscribeAsync(keys.Channel("ack"))).OnMessage(m =>
        {
            if (JsonSerializer.Deserialize<CommandAck>((byte[])m.Message!) is { } ack && _acks.TryRemove(ack.CmdId, out var tcs)) tcs.TrySetResult(ack);
        });
        (await sub.SubscribeAsync(keys.Channel("cfg"))).OnMessage(m =>
        {
            if (JsonSerializer.Deserialize<ConfigMessage>((byte[])m.Message!) is { } c && _local.ContainsKey(c.Serial)) _configs[c.Serial] = (c.State, c.At);
        });
    }

    private void Publish(RedisChannel channel, byte[] message)
    {
        try { redis.GetSubscriber().Publish(channel, message, CommandFlags.FireAndForget); }
        catch (Exception ex) { log.LogWarning("could not publish to {Channel}: {Message}", channel, ex.Message); }
    }
}

/// <summary>
/// Plan B5: device frames go through one Redis channel; every instance pushes them to its own WebSocket clients. The
/// sender gets its own frames back the same way, so each client receives a frame once. Frames are only built for devices
/// some client watches on some instance: each instance shares its watched devices every second (hash rtwatch).
/// </summary>
public sealed class RedisRealtimeBus(IConnectionMultiplexer redis, RedisKeys keys, RealtimeHub hub, TimeProvider clock, ILogger<RedisRealtimeBus> log)
    : IRealtimeBus, IRedisSubscriber
{
    /// <summary>A watch list not refreshed for this long belongs to an instance that stopped.</summary>
    private static readonly TimeSpan WatchExpiry = TimeSpan.FromSeconds(5);
    private sealed record WatchList(DateTimeOffset At, string[] Serials);

    private readonly string _instance = Guid.NewGuid().ToString("N");
    private volatile HashSet<string> _watchedAnywhere = new(StringComparer.OrdinalIgnoreCase);

    public void Publish(string serial, byte[] message)
    {
        // "<serial>\n<frame>": serials never contain a line break.
        var packet = new byte[serial.Length + 1 + message.Length];
        Encoding.ASCII.GetBytes(serial, packet);
        packet[serial.Length] = (byte)'\n';
        message.CopyTo(packet, serial.Length + 1);
        try { redis.GetSubscriber().Publish(keys.Channel("rt"), packet, CommandFlags.FireAndForget); }
        catch (Exception ex) { log.LogDebug("could not publish a frame of {Serial}: {Message}", serial, ex.Message); }
    }

    // Building and sending a frame per message for every device cost most of a core at 5.000 devices (LOADTEST B7);
    // only watched devices need them. A client that just subscribed has its snapshot and gets frames within ~1 s.
    public bool MayHaveListeners(string serial) => hub.HasListeners(serial) || _watchedAnywhere.Contains(serial);

    /// <summary>Shares this instance's watched devices and reads everyone's; called every second by <see cref="LiveStoreSync"/>.</summary>
    public async Task RefreshWatchAsync()
    {
        var db = redis.GetDatabase();
        var now = clock.GetUtcNow();
        try
        {
            await db.HashSetAsync(keys.Watch, _instance, JsonSerializer.SerializeToUtf8Bytes(new WatchList(now, hub.Watched)));
            var all = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var e in await db.HashGetAllAsync(keys.Watch))
            {
                if (JsonSerializer.Deserialize<WatchList>((byte[])e.Value!) is not { } w) continue;
                if (now - w.At > WatchExpiry) { await db.HashDeleteAsync(keys.Watch, e.Name); continue; }
                all.UnionWith(w.Serials);
            }
            _watchedAnywhere = all;
        }
        catch (Exception ex) { log.LogDebug("could not share the watched devices: {Message}", ex.Message); }
    }

    public Task ForgetWatchAsync() => redis.GetDatabase().HashDeleteAsync(keys.Watch, _instance);

    public async Task SubscribeAsync()
    {
        (await redis.GetSubscriber().SubscribeAsync(keys.Channel("rt"))).OnMessage(m =>
        {
            var packet = (byte[])m.Message!;
            var cut = Array.IndexOf(packet, (byte)'\n');
            if (cut <= 0) return;
            var serial = Encoding.ASCII.GetString(packet, 0, cut);
            if (hub.HasListeners(serial)) hub.Publish(serial, packet[(cut + 1)..]);
        });
    }
}

/// <summary>A Redis-backed service that listens on channels.</summary>
public interface IRedisSubscriber
{
    Task SubscribeAsync();
}

/// <summary>
/// Subscribes the Redis listeners when the host starts, before it takes traffic; if Redis is down then, keeps trying in
/// the background (StackExchange.Redis restores subscriptions by itself after later reconnects).
/// </summary>
public sealed class RedisSubscriptions(IServiceProvider services, Microsoft.Extensions.Options.IOptions<SharedOptions> options, ILogger<RedisSubscriptions> log) : BackgroundService
{
    private readonly TaskCompletionSource _first = new(TaskCreationOptions.RunContinuationsAsynchronously);

    public override async Task StartAsync(CancellationToken ct)
    {
        await base.StartAsync(ct);
        await Task.WhenAny(_first.Task, Task.Delay(TimeSpan.FromSeconds(5), ct));
    }

    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        if (options.Value.Provider != "redis") { _first.TrySetResult(); return; }
        IRedisSubscriber[] subscribers =
        [
            (IRedisSubscriber)services.GetService(typeof(RedisCommandCoordinator))!,
            (IRedisSubscriber)services.GetService(typeof(RedisRealtimeBus))!,
            (IRedisSubscriber)services.GetService(typeof(RedisLiveStore))!,
        ];
        foreach (var s in subscribers)
        {
            while (!stop.IsCancellationRequested)
            {
                try { await s.SubscribeAsync(); break; }
                catch (Exception ex)
                {
                    log.LogWarning("Redis subscription of {Service} failed, retrying: {Message}", s.GetType().Name, ex.Message);
                    await Task.Delay(TimeSpan.FromSeconds(2), stop).ContinueWith(_ => { }, CancellationToken.None);
                }
            }
        }
        _first.TrySetResult();
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
