using System.Threading.Channels;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace GgPower.Infrastructure;

/// <summary>
/// Database work for device events (status, config state, firmware, alarms), done off the MQTT receive path (plan A6):
/// the handler updates memory and the realtime channel at once and queues the writes here, so a slow database does not
/// slow down receiving. One consumer runs the items in arrival order, which keeps each device's events in order.
/// </summary>
public sealed class DeviceEventQueue(ILogger<DeviceEventQueue> log) : BackgroundService
{
    /// <summary>Events waiting before status / firmware updates are dropped (alarms then wait for room instead).</summary>
    public const int Capacity = 10_000;

    private sealed record Item(string What, Func<CancellationToken, Task> Work);

    private readonly Channel<Item> _queue = Channel.CreateBounded<Item>(
        new BoundedChannelOptions(Capacity) { SingleReader = true, FullMode = BoundedChannelFullMode.Wait });
    private long _pending;
    private long _dropped;
    private long _failed;

    public long Pending => Interlocked.Read(ref _pending);
    public long Dropped => Interlocked.Read(ref _dropped);
    public long Failed => Interlocked.Read(ref _failed);

    /// <summary>
    /// Queues <paramref name="work"/>. <paramref name="mayDrop"/>: superseded by the next message of the same kind
    /// (status, firmware), so it is dropped when the queue is full; otherwise (alarms) the caller waits for room.
    /// </summary>
    public async ValueTask EnqueueAsync(string what, Func<CancellationToken, Task> work, bool mayDrop, CancellationToken ct)
    {
        var item = new Item(what, work);
        Interlocked.Increment(ref _pending);
        if (mayDrop)
        {
            if (_queue.Writer.TryWrite(item)) return;
            Interlocked.Decrement(ref _pending);
            Interlocked.Increment(ref _dropped);
            log.LogWarning("device event queue full: {What} dropped", what);
            return;
        }
        try { await _queue.Writer.WriteAsync(item, ct); }
        catch { Interlocked.Decrement(ref _pending); throw; }
    }

    /// <summary>Waits until everything queued so far has been written (tests; shutdown).</summary>
    public async Task DrainAsync(CancellationToken ct = default)
    {
        while (Pending > 0) await Task.Delay(5, ct);
    }

    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        try
        {
            await foreach (var item in _queue.Reader.ReadAllAsync(stop)) await RunAsync(item, stop);
        }
        catch (OperationCanceledException) when (stop.IsCancellationRequested) { }

        // Shutdown: MQTT has stopped before this service (reverse start order); write what is left, within 10 s.
        using var budget = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        while (!budget.IsCancellationRequested && _queue.Reader.TryRead(out var rest)) await RunAsync(rest, budget.Token);
    }

    private async Task RunAsync(Item item, CancellationToken ct)
    {
        try
        {
            // The database may be briefly away: retry a few times before giving the event up.
            for (var attempt = 0; ; attempt++)
            {
                try { await item.Work(ct); return; }
                catch (Exception ex) when (attempt < 4 && !ct.IsCancellationRequested)
                {
                    log.LogWarning(ex, "device event {What} failed, retrying", item.What);
                    await Task.Delay(TimeSpan.FromMilliseconds(500 * (1 << attempt)), ct);
                }
            }
        }
        catch (Exception ex)
        {
            Interlocked.Increment(ref _failed);
            log.LogError(ex, "device event {What} could not be written", item.What);
        }
        finally
        {
            Interlocked.Decrement(ref _pending);
        }
    }
}
