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
    /// <summary>S3-compatible test server: docker start ggpower-s3-test (SeaweedFS, see PROGRESS.md section 5).</summary>
    public static string S3Address => Environment.GetEnvironmentVariable("GGP_TEST_S3") ?? "http://localhost:8334";

    public AppFixture A { get; private set; } = null!;
    public AppFixture B { get; private set; } = null!;

    public async Task InitializeAsync()
    {
        var settings = new Dictionary<string, string?>
        {
            ["Shared:Provider"] = "redis",
            ["Shared:Redis"] = RedisSharedTests.Address,
            ["Shared:KeyPrefix"] = $"ggp-test-{Guid.NewGuid():N}:",
            // B6: files in an S3 bucket (SeaweedFS test container), one bucket per run.
            ["Storage:Provider"] = "s3",
            ["Storage:S3:ServiceUrl"] = S3Address,
            ["Storage:S3:Bucket"] = $"ggp-test-{Guid.NewGuid():N}",
            ["Storage:S3:AccessKey"] = Environment.GetEnvironmentVariable("GGP_TEST_S3_KEY") ?? "test",
            ["Storage:S3:SecretKey"] = Environment.GetEnvironmentVariable("GGP_TEST_S3_SECRET") ?? "test",
        };
        // The layout of plan B7: A receives the device data, B only serves the API.
        A = new ExtraHost(new(settings) { ["Roles"] = "api,ingest" }, 500000);
        B = new ExtraHost(new(settings) { ["Roles"] = "api" }, 600000);
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
public partial class MultiInstanceTests(AppFixture app, RedisInstances hosts) : IClassFixture<RedisInstances>
{
    private AppFixture A => hosts.A;
    private AppFixture B => hosts.B;

    /// <summary>A device fed into A becomes visible to B once A has written its shared state (every 250 ms).</summary>
    private async Task SharedWithB(string serial)
    {
        var store = B.Services.GetRequiredService<ILiveStore>();
        // Complete: an early copy may predate the device's settings or telemetry (and is cached for 500 ms).
        for (var i = 0; i < 60 && await store.GetAsync(serial) is not { ConfigState: not null, Telemetry: not null }; i++) await Task.Delay(50);
        Assert.NotNull((await store.GetAsync(serial))?.ConfigState);
    }

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

        // A builds frames once it knows some instance watches the device (shared every second): keep the data coming.
        using var done = new CancellationTokenSource();
        var feeder = Task.Run(async () =>
        {
            while (!done.IsCancellationRequested)
            {
                await A.Feed(serial, "telemetry", AppFixture.Telemetry(serial, pv: 4321));
                await Task.Delay(300);
            }
        });
        JsonNode frame;
        do frame = await Receive(ws, 10);
        while ((string?)frame["type"] != "device" || (double?)frame["telemetry"]?["pv_w"] != 4321);
        done.Cancel();
        await feeder;
        Assert.Equal(serial, (string)frame["serial"]!);
    }

    // B5, C2: a command sent through instance B, which receives no device data (it reads the shared live state), is
    // acknowledged and read back at instance A, where the device's data arrives; B reports it applied.
    [Fact]
    public async Task ACommandSentOnOneInstanceIsConfirmedThroughAnother()
    {
        var owner = await app.NewUser();
        var serial = await A.NewDevice();
        await A.NewProject(owner, serial);
        await app.Allow(serial);
        await SharedWithB(serial);
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
        await SharedWithB(serial);

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

public partial class MultiInstanceTests
{
    // B6: a ticket picture uploaded through instance A can be opened through instance B.
    [Fact]
    public async Task AFileUploadedOnOneInstanceIsServedByAnother()
    {
        var user = await app.NewUser();
        var id = (string)(await A.Post("/api/v1/tickets", new { type = "other", title = "T", description = "D" }, user.Access)).Body!["id"]!;
        var png = new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }.Concat(Enumerable.Range(0, 200).Select(i => (byte)i)).ToArray();
        var (s, body, _) = await A.Send(HttpMethod.Post, $"/api/v1/tickets/{id}/attachments", png, user.Access);
        Assert.Equal(HttpStatusCode.Created, s);

        var res = await B.Get($"/api/v1/tickets/{id}/attachments/{(string)body!["id"]!}", user.Access);
        Assert.Equal(HttpStatusCode.OK, res.Status);
        Assert.Equal("image/png", res.Raw.Content.Headers.ContentType!.MediaType);
        Assert.Equal(png, await res.Raw.Content.ReadAsByteArrayAsync());
    }

    [Fact]
    public async Task TheS3StoreSavesOpensAndDeletes()
    {
        var store = A.Services.GetRequiredService<GgPower.Api.IFileStore>();
        Assert.IsType<GgPower.Api.S3FileStore>(store);
        var data = Encoding.UTF8.GetBytes("RIFF....WEBP fake picture");
        var name = await store.SaveAsync("projects", data, "image/webp", CancellationToken.None);
        Assert.EndsWith(".webp", name);

        await using (var read = await store.OpenAsync("projects", name, CancellationToken.None))
        {
            using var ms = new MemoryStream();
            await read!.CopyToAsync(ms);
            Assert.Equal(data, ms.ToArray());
        }
        Assert.Null(await store.OpenAsync("projects", "../" + name, CancellationToken.None));   // not a name the store made
        Assert.Null(await store.OpenAsync("avatars", name, CancellationToken.None));            // other area
        await store.DeleteAsync("projects", name, CancellationToken.None);
        Assert.Null(await store.OpenAsync("projects", name, CancellationToken.None));
    }
}

public partial class MultiInstanceTests
{
    // C2: an instance that receives no device data answers from the live state the receiving instance shares.
    [Fact]
    public async Task AnInstanceWithoutTheDataAnswersFromTheSharedState()
    {
        var owner = await app.NewUser();
        var serial = await A.NewDevice();
        var project = await A.NewProject(owner, serial);
        await A.Feed(serial, "telemetry", AppFixture.Telemetry(serial, pv: 2500, load: 700));

        JsonNode? body = null;
        for (var i = 0; i < 40; i++)
        {
            body = (await B.Get($"/api/v1/projects/{project}/overview", owner.Access)).Body;
            if ((double?)body?["energy"]?["pv_kw"] == 2.5) break;
            await Task.Delay(100);
        }
        Assert.Equal(("online", "Test Inverter", 2.5, 0.7),
            ((string)body!["status"]!, (string)body["model"]!, (double)body["energy"]!["pv_kw"]!, (double)body["energy"]!["load_kw"]!));
        // Settings come along (the config screen of B shows the device's version).
        Assert.Equal(await A.Version(owner, serial), await B.Version(owner, serial));
        // B never received the device's data itself.
        Assert.False(B.Services.GetRequiredService<LiveStateCache>().Get(serial)?.Fed ?? false);
    }
}

public partial class MultiInstanceTests
{
    // B7: roles from the setting; an instance without the jobs role never takes a job.
    [Fact]
    public async Task RolesDecideWhatAnInstanceDoes()
    {
        var all = new InstanceRoles(null);
        Assert.True(all.ServesApi && all.Ingests && all.RunsJobs);
        var apiOnly = new InstanceRoles(" API ");
        Assert.True(apiOnly.ServesApi && !apiOnly.Ingests && !apiOnly.RunsJobs);
        Assert.Equal("api,jobs", new InstanceRoles("jobs,api").ToString());
        Assert.Throws<InvalidOperationException>(() => new InstanceRoles("api,worker"));

        Assert.Equal("api", B.Services.GetRequiredService<InstanceRoles>().ToString());
        var leader = B.Services.GetRequiredService<JobLeader>();
        Assert.False(await leader.TryLeadAsync(JobLeader.Rollup, CancellationToken.None));
        Assert.DoesNotContain(JobLeader.Jobs, leader.Holds);
        Assert.False(B.Services.GetRequiredService<MqttLink>().Required);   // readiness does not wait for MQTT on B
    }
}
