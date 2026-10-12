using GgPower.Domain;
using GgPower.Infrastructure;
using StackExchange.Redis;

namespace GgPower.Tests;

/// <summary>
/// Plan B4: two "instances" (separate Redis connections) see the same tickets, command locks and cache versions.
/// Needs the Redis test container: docker start ggpower-redis-test (port 6380, see PROGRESS.md section 5).
/// </summary>
public sealed class RedisSharedTests : IAsyncLifetime
{
    public static string Address => Environment.GetEnvironmentVariable("GGP_TEST_REDIS") ?? "localhost:6380";

    private ConnectionMultiplexer _a = null!, _b = null!;
    private RedisKeys _keys = null!;

    public async Task InitializeAsync()
    {
        try
        {
            _a = await ConnectionMultiplexer.ConnectAsync(Address);
            _b = await ConnectionMultiplexer.ConnectAsync(Address);
        }
        catch (RedisConnectionException ex)
        {
            throw new InvalidOperationException($"Redis is needed at {Address} (docker start ggpower-redis-test).", ex);
        }
        _keys = new RedisKeys($"ggp-test-{Guid.NewGuid():N}:");
    }

    public async Task DisposeAsync()
    {
        await _a.DisposeAsync();
        await _b.DisposeAsync();
    }

    [Fact]
    public async Task ATicketIssuedOnOneInstanceWorksOnceOnAnother()
    {
        var onA = new RedisTicketStore(_a, _keys);
        var onB = new RedisTicketStore(_b, _keys);
        var user = Guid.NewGuid();
        var ticket = await onA.IssueAsync(user, "owner", TimeSpan.FromSeconds(30));

        Assert.Equal((user, "owner"), await onB.RedeemAsync(ticket));
        Assert.Null(await onA.RedeemAsync(ticket));   // used up
        Assert.Null(await onB.RedeemAsync("not-a-ticket"));

        var shortLived = await onA.IssueAsync(user, "owner", TimeSpan.FromMilliseconds(50));
        await Task.Delay(200);
        Assert.Null(await onB.RedeemAsync(shortLived));
    }

    [Fact]
    public async Task OneCommandPerDeviceAcrossInstances()
    {
        var onA = new RedisCommandCoordinator(_a, _keys);
        var onB = new RedisCommandCoordinator(_b, _keys);
        Guid first = Guid.NewGuid(), second = Guid.NewGuid();

        Assert.Null(await onA.TryLockAsync("dev-1", first, TimeSpan.FromMinutes(1)));
        Assert.Equal(first, await onB.TryLockAsync("DEV-1", second, TimeSpan.FromMinutes(1)));   // same device, any case
        Assert.Null(await onB.TryLockAsync("dev-2", second, TimeSpan.FromMinutes(1)));            // another device is free

        await onB.UnlockAsync("dev-1", second);   // not the holder: no effect
        Assert.Equal(first, await onB.TryLockAsync("dev-1", second, TimeSpan.FromMinutes(1)));
        await onA.UnlockAsync("dev-1", first);
        Assert.Null(await onB.TryLockAsync("dev-1", second, TimeSpan.FromMinutes(1)));

        // A holder that disappears frees the device when its lock expires.
        Assert.Null(await onA.TryLockAsync("dev-3", first, TimeSpan.FromMilliseconds(100)));
        await Task.Delay(250);
        Assert.Null(await onB.TryLockAsync("dev-3", second, TimeSpan.FromMinutes(1)));
    }

    [Fact]
    public async Task TheMinimumIntervalHoldsAcrossInstances()
    {
        var onA = new RedisCommandCoordinator(_a, _keys);
        var onB = new RedisCommandCoordinator(_b, _keys);
        Assert.Null(await onB.RetryAfterAsync("dev-1", TimeSpan.FromSeconds(5)));
        await onA.StartIntervalAsync("dev-1", TimeSpan.FromSeconds(5));
        Assert.InRange(await onB.RetryAfterAsync("dev-1", TimeSpan.FromSeconds(5)) ?? TimeSpan.Zero, TimeSpan.FromSeconds(4), TimeSpan.FromSeconds(5));
    }

    [Fact]
    public async Task CacheVersionsAreShared()
    {
        var onA = new RedisCacheVersions(_a, _keys);
        var onB = new RedisCacheVersions(_b, _keys);
        Assert.Equal(0, await onB.VersionAsync("dev-1"));
        await onA.BumpAsync(["dev-1", "dev-2"]);
        await onA.BumpAsync(["DEV-1"]);
        Assert.Equal(2, await onB.VersionAsync("dev-1"));
        Assert.Equal(1, await onB.VersionAsync("dev-2"));
        Assert.Equal(0, await onB.VersionAsync(null));
    }
}
