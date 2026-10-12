using System.Collections.Concurrent;
using System.Text.Json;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using StackExchange.Redis;

namespace GgPower.Infrastructure;

/// <summary>
/// Live state shared through Redis (plan C2). The instance that receives a device's data keeps the current copy in
/// memory and writes it to the hash <c>dev:{serial}</c> (fields s = state, c = settings, f = firmware), a few times a
/// second at most; any other instance reads that hash, cached for <see cref="ReadCache"/>.
/// </summary>
public sealed class RedisLiveStore(IConnectionMultiplexer redis, RedisKeys keys, LiveStateCache live, Db db, TimeProvider clock, ILogger<RedisLiveStore> log)
    : ILiveStore, IRedisSubscriber
{
    public static readonly TimeSpan ReadCache = TimeSpan.FromMilliseconds(500);
    /// <summary>Every part is written again after this long, so a restarted (empty) Redis fills up again.</summary>
    public static readonly TimeSpan FullRewriteEvery = TimeSpan.FromMinutes(5);
    private static readonly TimeSpan KeepFor = TimeSpan.FromDays(30);

    private sealed record ConfigPart(JsonElement? State, DateTimeOffset? At);

    private readonly ConcurrentDictionary<string, (DeviceLive Device, LiveParts Parts)> _dirty = new(StringComparer.OrdinalIgnoreCase);
    private readonly ConcurrentDictionary<string, DateTimeOffset> _lastFull = new(StringComparer.OrdinalIgnoreCase);
    private readonly ConcurrentDictionary<string, (DeviceLive? Device, DateTimeOffset Until)> _cache = new(StringComparer.OrdinalIgnoreCase);

    public async Task<DeviceLive?> GetAsync(string serial, CancellationToken ct = default) => (await GetManyAsync([serial], ct))[0];

    public async Task<DeviceLive?[]> GetManyAsync(IReadOnlyList<string> serials, CancellationToken ct = default)
    {
        var result = new DeviceLive?[serials.Count];
        var now = clock.GetUtcNow();
        var fetch = new List<int>();
        for (var i = 0; i < serials.Count; i++)
        {
            // Data received here: this copy is the current one.
            if (live.Get(serials[i]) is { Fed: true } own) result[i] = own;
            else if (_cache.TryGetValue(serials[i], out var hit) && hit.Until > now) result[i] = hit.Device;
            else fetch.Add(i);
        }
        if (fetch.Count == 0) return result;

        var database = redis.GetDatabase();
        var reads = fetch.Select(i => database.HashGetAllAsync(keys.Device(serials[i]))).ToArray();
        await Task.WhenAll(reads);
        for (var k = 0; k < fetch.Count; k++)
        {
            var i = fetch[k];
            // Not in Redis (not heard from since Redis started): what this instance loaded from the database at start-up.
            var d = Read(serials[i], reads[k].Result) ?? live.Get(serials[i]);
            _cache[serials[i]] = (d, now + ReadCache);
            result[i] = d;
        }
        return result;
    }

    private DeviceLive? Read(string serial, HashEntry[] fields)
    {
        if (fields.Length == 0) return null;
        try
        {
            var map = fields.ToDictionary(f => (string)f.Name!, f => (byte[])f.Value!);
            if (!map.TryGetValue("s", out var s) || JsonSerializer.Deserialize<DeviceLive>(s) is not { } d) return null;
            if (map.TryGetValue("c", out var c) && JsonSerializer.Deserialize<ConfigPart>(c) is { } cfg)
            {
                d.ConfigState = cfg.State;
                d.ConfigStateAt = cfg.At;
            }
            if (map.TryGetValue("f", out var f)) d.Firmware = JsonSerializer.Deserialize<JsonElement?>(f);
            return d;
        }
        catch (JsonException ex)
        {
            log.LogWarning("unreadable shared state of {Serial}: {Message}", serial, ex.Message);
            return null;
        }
    }

    public void Changed(DeviceLive d, LiveParts parts)
    {
        // A copy only loaded from the database must never overwrite the current one of the instance receiving the data.
        if (!d.Fed) return;
        _dirty.AddOrUpdate(d.Serial, (d, parts), (_, cur) => (d, cur.Parts | parts));
    }

    /// <summary>Writes the changed devices; called a few times a second by <see cref="LiveStoreSync"/>.</summary>
    public async Task FlushAsync()
    {
        if (_dirty.IsEmpty) return;
        var now = clock.GetUtcNow();
        var batch = redis.GetDatabase().CreateBatch();
        var writes = new List<Task>();
        foreach (var serial in _dirty.Keys)
        {
            if (!_dirty.TryRemove(serial, out var e)) continue;
            var parts = e.Parts;
            if (!_lastFull.TryGetValue(serial, out var full) || now - full > FullRewriteEvery)
            {
                parts = LiveParts.All;
                _lastFull[serial] = now;
            }
            var fields = new List<HashEntry>(3);
            lock (e.Device.Gate)
            {
                if (parts.HasFlag(LiveParts.State)) fields.Add(new("s", JsonSerializer.SerializeToUtf8Bytes(e.Device)));
                if (parts.HasFlag(LiveParts.Config))
                    fields.Add(new("c", JsonSerializer.SerializeToUtf8Bytes(new ConfigPart(e.Device.ConfigState, e.Device.ConfigStateAt))));
                if (parts.HasFlag(LiveParts.Firmware)) fields.Add(new("f", JsonSerializer.SerializeToUtf8Bytes(e.Device.Firmware)));
            }
            var key = keys.Device(serial);
            writes.Add(batch.HashSetAsync(key, [.. fields]));
            writes.Add(batch.KeyExpireAsync(key, KeepFor));
        }
        batch.Execute();
        try { await Task.WhenAll(writes); }
        catch (Exception ex)
        {
            // Write everything again once Redis is back.
            _lastFull.Clear();
            log.LogWarning("could not write the shared live state: {Message}", ex.Message);
        }
    }

    public async Task AlertsChangedAsync(string serial, CancellationToken ct)
    {
        _cache.TryRemove(serial, out _);
        await redis.GetSubscriber().PublishAsync(keys.Channel("recount"), serial);
    }

    public async Task SubscribeAsync()
    {
        (await redis.GetSubscriber().SubscribeAsync(keys.Channel("recount"))).OnMessage(async m =>
        {
            var serial = (string)m.Message!;
            _cache.TryRemove(serial, out _);
            if (live.Get(serial) is not { Fed: true } d) return;
            try
            {
                await using var c = await db.OpenAsync();
                await IngestPipeline.RecountAsync(c, d);
                Changed(d, LiveParts.State);
            }
            catch (Exception ex) { log.LogWarning("recount of {Serial} failed: {Message}", serial, ex.Message); }
        });
    }
}

/// <summary>Writes the shared live state of the devices received here, every 250 ms (Shared:Provider = redis only).</summary>
public sealed class LiveStoreSync(IServiceProvider services, IOptions<SharedOptions> options) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        if (options.Value.Provider != "redis") return;
        var store = (RedisLiveStore)services.GetService(typeof(RedisLiveStore))!;
        using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(250));
        try
        {
            while (await timer.WaitForNextTickAsync(stop)) await store.FlushAsync();
        }
        catch (OperationCanceledException) { }
        await store.FlushAsync();
    }
}
