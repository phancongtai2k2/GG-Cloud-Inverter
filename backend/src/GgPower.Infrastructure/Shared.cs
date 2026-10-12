using System.Collections.Concurrent;
using GgPower.Domain;

namespace GgPower.Infrastructure;

// State that must be the same on every API instance (plan B3). Each interface has an in-memory version, which is right
// for one instance and is the default (Shared:Provider = memory); the Redis versions come with B4 / B5.

/// <summary>One-time tickets that let a WebSocket client authenticate (browsers cannot send headers there).</summary>
public interface ITicketStore
{
    Task<string> IssueAsync(Guid userId, string role, TimeSpan lifetime);

    /// <summary>The ticket's owner, once: a ticket redeemed or expired gives null.</summary>
    Task<(Guid UserId, string Role)?> RedeemAsync(string? ticket);
}

/// <summary>
/// What keeps config commands for one device in order: one command at a time, a minimum interval between commands,
/// and handing each acknowledgement to whoever sent the command.
/// </summary>
public interface ICommandCoordinator
{
    /// <summary>Time left before the device takes another command, or null when it may take one now.</summary>
    Task<TimeSpan?> RetryAfterAsync(string serial, TimeSpan minInterval);

    /// <summary>Starts the minimum interval of the device.</summary>
    Task StartIntervalAsync(string serial, TimeSpan minInterval);

    /// <summary>
    /// Takes the device for command <paramref name="id"/>. Returns null on success, or the command that holds it.
    /// <paramref name="ttl"/> frees the device if the holder disappears.
    /// </summary>
    Task<Guid?> TryLockAsync(string serial, Guid id, TimeSpan ttl);

    Task UnlockAsync(string serial, Guid id);

    /// <summary>Starts waiting for the acknowledgement of <paramref name="cmdId"/>; call before publishing the command.</summary>
    AckWait Expect(string cmdId);

    /// <summary>An acknowledgement arrived from a device.</summary>
    void Deliver(CommandAck ack);

    /// <summary>The device reported its settings (it does after every write).</summary>
    void DeliverConfig(string serial, System.Text.Json.JsonElement state, DateTimeOffset at);

    /// <summary>
    /// The last settings reported by a device that this instance runs a command for (null otherwise): the command
    /// compares them with what it wrote.
    /// </summary>
    (System.Text.Json.JsonElement State, DateTimeOffset At)? LatestConfig(string serial);
}

/// <summary>A pending acknowledgement. Dispose it when done waiting.</summary>
public sealed class AckWait(Task<CommandAck> task, Action done) : IDisposable
{
    public Task<CommandAck> Task { get; } = task;
    public void Dispose() => done();
}

/// <summary>
/// Per-device version that is part of cache keys: bumped when old data of the device changed (late samples rolled up),
/// so cached statistics of that device are no longer used.
/// </summary>
public interface ICacheVersions
{
    Task<long> VersionAsync(string? serial);
    Task BumpAsync(IReadOnlyCollection<string> serials);
}

/// <summary>Sends a device's live frame to the apps watching it, whichever instance they are connected to.</summary>
public interface IRealtimeBus
{
    void Publish(string serial, byte[] message);

    /// <summary>False only when it is certain nobody watches the device (saves building the frame).</summary>
    bool MayHaveListeners(string serial);
}

public sealed class MemoryTicketStore(TimeProvider clock) : ITicketStore
{
    private readonly ConcurrentDictionary<string, (Guid UserId, string Role, DateTimeOffset Expires)> _tickets = new();

    public Task<string> IssueAsync(Guid userId, string role, TimeSpan lifetime)
    {
        var now = clock.GetUtcNow();
        foreach (var kv in _tickets) if (kv.Value.Expires < now) _tickets.TryRemove(kv.Key, out _);
        var ticket = Secrets.NewToken();
        _tickets[ticket] = (userId, role, now + lifetime);
        return Task.FromResult(ticket);
    }

    public Task<(Guid UserId, string Role)?> RedeemAsync(string? ticket)
    {
        if (string.IsNullOrEmpty(ticket) || !_tickets.TryRemove(ticket, out var t) || t.Expires < clock.GetUtcNow())
            return Task.FromResult<(Guid, string)?>(null);
        return Task.FromResult<(Guid, string)?>((t.UserId, t.Role));
    }
}

public sealed class MemoryCommandCoordinator(TimeProvider clock) : ICommandCoordinator
{
    private readonly ConcurrentDictionary<string, TaskCompletionSource<CommandAck>> _acks = new();
    private readonly ConcurrentDictionary<string, Guid> _running = new(StringComparer.OrdinalIgnoreCase);
    private readonly ConcurrentDictionary<string, DateTimeOffset> _lastCommand = new(StringComparer.OrdinalIgnoreCase);
    private readonly ConcurrentDictionary<string, (System.Text.Json.JsonElement State, DateTimeOffset At)> _configs = new(StringComparer.OrdinalIgnoreCase);

    public Task<TimeSpan?> RetryAfterAsync(string serial, TimeSpan minInterval)
    {
        var now = clock.GetUtcNow();
        return Task.FromResult(_lastCommand.TryGetValue(serial, out var last) && now - last < minInterval ? minInterval - (now - last) : (TimeSpan?)null);
    }

    public Task StartIntervalAsync(string serial, TimeSpan minInterval)
    {
        _lastCommand[serial] = clock.GetUtcNow();
        return Task.CompletedTask;
    }

    // One instance never loses a holder without it unlocking, so the ttl is not needed here.
    public Task<Guid?> TryLockAsync(string serial, Guid id, TimeSpan ttl) =>
        Task.FromResult(_running.TryAdd(serial, id) ? null : (Guid?)_running.GetValueOrDefault(serial));

    public Task UnlockAsync(string serial, Guid id)
    {
        if (_running.TryRemove(new KeyValuePair<string, Guid>(serial, id))) _configs.TryRemove(serial, out _);
        return Task.CompletedTask;
    }

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

    public void DeliverConfig(string serial, System.Text.Json.JsonElement state, DateTimeOffset at)
    {
        if (_running.ContainsKey(serial)) _configs[serial] = (state, at);
    }

    public (System.Text.Json.JsonElement State, DateTimeOffset At)? LatestConfig(string serial) =>
        _configs.TryGetValue(serial, out var c) ? c : null;
}

public sealed class MemoryCacheVersions : ICacheVersions
{
    private readonly ConcurrentDictionary<string, long> _versions = new(StringComparer.OrdinalIgnoreCase);

    public Task<long> VersionAsync(string? serial) =>
        Task.FromResult(serial is not null && _versions.TryGetValue(serial, out var v) ? v : 0);

    public Task BumpAsync(IReadOnlyCollection<string> serials)
    {
        foreach (var s in serials) _versions.AddOrUpdate(s, 1, (_, v) => v + 1);
        return Task.CompletedTask;
    }
}

/// <summary>One instance: the frame goes straight to this instance's WebSocket connections.</summary>
public sealed class LocalRealtimeBus(RealtimeHub hub) : IRealtimeBus
{
    public void Publish(string serial, byte[] message) => hub.Publish(serial, message);
    public bool MayHaveListeners(string serial) => hub.HasListeners(serial);
}

/// <summary>Parts of a device's live state, stored apart in the shared copy since they change at very different rates.</summary>
[Flags]
public enum LiveParts { State = 1, Config = 2, Firmware = 4, All = State | Config | Firmware }

/// <summary>
/// Reading a device's live state wherever its data is received (plan C2), and publishing changes made here. With one
/// instance it is simply this instance's <see cref="LiveStateCache"/>.
/// </summary>
public interface ILiveStore
{
    /// <summary>The latest state of the device, or null for a device never seen. Treat it as read-only.</summary>
    Task<DeviceLive?> GetAsync(string serial, CancellationToken ct = default);

    /// <summary>States of several devices, in the order given (null for unknown ones).</summary>
    Task<DeviceLive?[]> GetManyAsync(IReadOnlyList<string> serials, CancellationToken ct = default);

    /// <summary>This instance changed the state of a device whose data it receives.</summary>
    void Changed(DeviceLive d, LiveParts parts);

    /// <summary>Alerts of the device changed in the database: counters are recomputed where the device's data is received.</summary>
    Task AlertsChangedAsync(string serial, CancellationToken ct);
}

public sealed class MemoryLiveStore(LiveStateCache live, Db db) : ILiveStore
{
    public Task<DeviceLive?> GetAsync(string serial, CancellationToken ct = default) => Task.FromResult(live.Get(serial));

    public Task<DeviceLive?[]> GetManyAsync(IReadOnlyList<string> serials, CancellationToken ct = default) =>
        Task.FromResult(serials.Select(live.Get).ToArray());

    public void Changed(DeviceLive d, LiveParts parts) { }

    public async Task AlertsChangedAsync(string serial, CancellationToken ct)
    {
        if (live.Get(serial) is not { } d) return;
        await using var c = await db.OpenAsync(ct);
        await IngestPipeline.RecountAsync(c, d);
    }
}
