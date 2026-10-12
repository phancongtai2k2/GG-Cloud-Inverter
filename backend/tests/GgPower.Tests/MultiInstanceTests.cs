using System.Net;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using GgPower.Infrastructure;
using Microsoft.Extensions.DependencyInjection;

namespace GgPower.Tests;

/// <summary>
/// Two API instances sharing state through Redis (Shared:Provider = redis), on the database the main fixture prepared.
/// Needs the Redis test container (see <see cref="RedisSharedTests"/>).
/// </summary>
public sealed class RedisInstances : IAsyncLifetime
{
    public AppFixture A { get; private set; } = null!;
    public AppFixture B { get; private set; } = null!;

    public async Task InitializeAsync()
    {
        var settings = new Dictionary<string, string?>
        {
            ["Shared:Provider"] = "redis",
            ["Shared:Redis"] = RedisSharedTests.Address,
            ["Shared:KeyPrefix"] = $"ggp-test-{Guid.NewGuid():N}:",
        };
        A = new ExtraHost(settings, 500000);
        B = new ExtraHost(settings, 600000);
        // Start both hosts (and their Redis subscriptions) before the tests talk to them.
        _ = A.Server;
        _ = B.Server;
        foreach (var host in new[] { A, B })
            Assert.IsType<RedisRealtimeBus>(host.Services.GetRequiredService<IRealtimeBus>());
        await Task.CompletedTask;
    }

    public async Task DisposeAsync()
    {
        await A.DisposeAsync();
        await B.DisposeAsync();
    }
}

/// <summary>A further instance of the application, next to the main fixture.</summary>
public sealed class ExtraHost(Dictionary<string, string?> settings, int firstNumber) : AppFixture(settings, firstNumber);

[Collection("app")]
public class MultiInstanceTests(AppFixture app, RedisInstances hosts) : IClassFixture<RedisInstances>
{
    private AppFixture A => hosts.A;
    private AppFixture B => hosts.B;

    private static async Task<JsonNode> Receive(WebSocket ws, int seconds = 5)
    {
        var buffer = new byte[64 * 1024];
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(seconds));
        var res = await ws.ReceiveAsync(buffer, cts.Token);
        return JsonNode.Parse(Encoding.UTF8.GetString(buffer, 0, res.Count))!;
    }

    // B5: an app connected to instance B gets the frames of a device whose data arrives at instance A; the ticket came
    // from A (B4).
    [Fact]
    public async Task AWebSocketOnOneInstanceGetsFramesFromAnother()
    {
        var owner = await app.NewUser();
        var serial = await A.NewDevice();
        var project = await A.NewProject(owner, serial);

        var ticket = (string)(await A.Post("/api/v1/realtime/ticket", null, owner.Access)).Body!["ticket"]!;
        using var ws = await B.Server.CreateWebSocketClient().ConnectAsync(new Uri(B.Server.BaseAddress, "/ws/v1/realtime?ticket=" + ticket), CancellationToken.None);
        await ws.SendAsync(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new { subscribe = new[] { project } })), WebSocketMessageType.Text, true, CancellationToken.None);
        Assert.Equal(serial, (string)Assert.Single((await Receive(ws))["serials"]!.AsArray())!);

        await A.Feed(serial, "telemetry", AppFixture.Telemetry(serial, pv: 4321));
        JsonNode frame;
        do frame = await Receive(ws);
        while ((string?)frame["type"] != "device" || (double?)frame["telemetry"]?["pv_w"] != 4321);
        Assert.Equal(serial, (string)frame["serial"]!);
    }

    // B5: a command sent through instance B is acknowledged and read back at instance A (where the device's MQTT data
    // arrives); B still reports it applied. Instance B also has the device's live state, as C2 will give every instance.
    [Fact]
    public async Task ACommandSentOnOneInstanceIsConfirmedThroughAnother()
    {
        var owner = await app.NewUser();
        var serial = await A.NewDevice();
        await A.NewProject(owner, serial);
        await app.Allow(serial);
        await B.Feed(serial, "status", new { sn = serial, state = "online", model = "Test Inverter", fw_ver = "V1", logger_fw = "1.0.16", inverter_comm = "ok" });
        await B.Feed(serial, "config/state", A.Devices.State[serial].ToJsonString());
        await B.Feed(serial, "telemetry", AppFixture.Telemetry(serial));
        // The device answers B's commands through A.
        B.Devices.State[serial] = A.Devices.State[serial];
        B.Devices.Services = A.Services;

        var cmd = await B.Command(owner, serial, new { group = "operating_mode", based_on_version = await B.Version(owner, serial), mode = "self_consumption" });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.Contains(B.Devices.Received, r => r.Serial == serial);
        Assert.DoesNotContain(A.Devices.Received, r => r.Serial == serial);
    }

    // B4: two instances never run commands on one device at the same time.
    [Fact]
    public async Task TwoInstancesCannotCommandOneDeviceAtOnce()
    {
        var owner = await app.NewUser();
        var serial = await A.NewDevice(FakeDevices.Behaviour.Silent);
        await A.NewProject(owner, serial);
        await app.Allow(serial);
        await B.Feed(serial, "status", new { sn = serial, state = "online", model = "Test Inverter", fw_ver = "V1", logger_fw = "1.0.16", inverter_comm = "ok" });
        await B.Feed(serial, "config/state", A.Devices.State[serial].ToJsonString());
        await B.Feed(serial, "telemetry", AppFixture.Telemetry(serial));

        var version = await A.Version(owner, serial);
        var body = new { group = "operating_mode", based_on_version = version, mode = "self_consumption" };
        var onA = await A.Post($"/api/v1/devices/{serial}/config-commands", body, owner.Access, new() { ["Idempotency-Key"] = Guid.NewGuid().ToString() });
        Assert.Equal(HttpStatusCode.Accepted, onA.Status);
        var onB = await B.Post($"/api/v1/devices/{serial}/config-commands", body, owner.Access, new() { ["Idempotency-Key"] = Guid.NewGuid().ToString() });
        Assert.Equal(HttpStatusCode.Conflict, onB.Status);
        Assert.Equal("COMMAND_IN_PROGRESS", (string)onB.Body!["code"]!);
        Assert.Equal((string)onA.Body!["command_id"]!, (string)onB.Body!["command_id"]!);
    }
}
