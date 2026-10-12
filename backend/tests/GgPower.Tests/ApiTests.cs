using System.Collections.Concurrent;
using System.Net;
using System.Net.Http.Headers;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Dapper;
using GgPower.Infrastructure;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Hosting;
using Npgsql;

namespace GgPower.Tests;

/// <summary>Captures one-time codes instead of sending them.</summary>
public sealed class CapturingOtpSender : IOtpSender
{
    public readonly ConcurrentDictionary<string, string> Codes = new();
    public Task SendAsync(string destination, string code, string purpose, CancellationToken ct)
    {
        Codes[destination] = code;
        return Task.CompletedTask;
    }
}

/// <summary>
/// Stands in for the broker and the dataloggers: commands the backend publishes are executed against an
/// in-memory copy of each device's settings, and the acknowledgement and new settings are fed back into
/// the ingest pipeline, as a real datalogger does.
/// </summary>
public sealed class FakeDevices : IMqttPublisher
{
    public enum Behaviour { Normal, RefuseValue, Silent, AckButIgnore }

    public IServiceProvider? Services;
    public readonly ConcurrentDictionary<string, JsonObject> State = new();
    public readonly ConcurrentDictionary<string, Behaviour> Mode = new();
    public readonly ConcurrentQueue<(string Serial, string Action, JsonObject Params)> Received = new();
    public bool IsConnected => true;

    public static JsonObject InitialState(string serial) => new()
    {
        ["sn"] = serial, ["grid_mode"] = "limit_ups_load", ["pv_priority"] = "charging", ["battery_mode"] = "ups_load",
        ["schedule_days"] = new JsonArray("mon", "tue", "wed", "thu", "fri", "sat", "sun"),
        ["battery_reserve_soc"] = 10, ["battery_resume_soc"] = 20, ["battery_reserve_writable"] = true,
        ["charge_schedule"] = new JsonObject
        {
            ["enabled"] = false,
            ["periods"] = new JsonArray(Enumerable.Range(1, 3).Select(i => (JsonNode)new JsonObject
            {
                ["period"] = i, ["start_time"] = "00:00", ["end_time"] = "00:00", ["charge_power_w"] = 3300, ["stop_soc"] = 100,
            }).ToArray()),
        },
        ["discharge_schedule"] = new JsonObject
        {
            ["enabled"] = false,
            ["periods"] = new JsonArray(Enumerable.Range(1, 3).Select(i => (JsonNode)new JsonObject
            {
                ["period"] = i, ["start_time"] = "00:00", ["end_time"] = "00:00", ["discharge_power_w"] = 6000, ["stop_soc"] = 10,
            }).ToArray()),
        },
        ["battery_reserve_soc"] = 10,
    };

    public Task PublishAsync(string topic, string payload, CancellationToken ct)
    {
        var parts = topic.Split('/');
        var serial = parts[3];
        var cmd = JsonNode.Parse(payload)!.AsObject();
        var action = (string)cmd["action"]!;
        var p = cmd["params"]!.AsObject();
        Received.Enqueue((serial, action, p));
        var behaviour = Mode.GetValueOrDefault(serial, Behaviour.Normal);
        if (behaviour == Behaviour.Silent) return Task.CompletedTask;

        var state = State.GetOrAdd(serial, InitialState);
        var refused = behaviour == Behaviour.RefuseValue;
        if (behaviour == Behaviour.Normal) Apply(state, action, p);
        var ack = new JsonObject
        {
            ["cmd_id"] = (string)cmd["cmd_id"]!, ["sn"] = serial, ["action"] = action, ["success"] = !refused, ["code"] = refused ? 500 : 200,
            ["message"] = refused ? "Biến tần từ chối giá trị (ILLEGAL OPERATION)" : "OK",
        };
        var pipeline = Services!.GetRequiredService<IngestPipeline>();
        _ = Task.Run(async () =>
        {
            await Task.Delay(30);
            await pipeline.HandleAsync($"solar/ggp/inverter/{serial}/up/command/ack", Encoding.UTF8.GetBytes(ack.ToJsonString()), CancellationToken.None);
            if (!refused)
                await pipeline.HandleAsync($"solar/ggp/inverter/{serial}/up/config/state", Encoding.UTF8.GetBytes(state.ToJsonString()), CancellationToken.None);
        });
        return Task.CompletedTask;
    }

    private static void Apply(JsonObject state, string action, JsonObject p)
    {
        lock (state)
        {
            if (action == "SET_WORK_MODE")
            {
                foreach (var key in new[] { "pv_priority", "grid_mode", "battery_mode" })
                    if (p[key] is { } v) state[key] = v.DeepClone();
            }
            else if (action == "SET_BATTERY_RESERVE")
            {
                var reserve = (int)p["reserve_soc"]!;
                var gap = (int)state["battery_resume_soc"]! - (int)state["battery_reserve_soc"]!;
                state["battery_reserve_soc"] = reserve;
                state["battery_resume_soc"] = Math.Min(100, reserve + gap);
            }
            else if (action == "SET_SCHEDULE_DAYS")
            {
                // a datalogger before 1.0.24 does not know the command (the caller must not send it)
                if (state["schedule_days"] is not null && p["days"] is { } days) state["schedule_days"] = days.DeepClone();
            }
            else if (action is "SET_CHARGE_SCHEDULE" or "SET_DISCHARGE_SCHEDULE")
            {
                var charge = action == "SET_CHARGE_SCHEDULE";
                // a datalogger without timed-discharge support does not know the command (the caller must not send it)
                if (state[charge ? "charge_schedule" : "discharge_schedule"] is not JsonObject schedule) return;
                if (p["enabled"] is { } en) schedule["enabled"] = en.DeepClone();
                if (p["periods"] is JsonArray periods)
                {
                    var target = schedule["periods"]!.AsArray();
                    for (var i = 0; i < periods.Count && i < 3; i++)
                    {
                        var slot = periods[i]!["period"] is { } n ? (int)n - 1 : i;
                        foreach (var key in new[] { "start_time", "end_time", charge ? "charge_power_w" : "discharge_power_w", "stop_soc" })
                            if (periods[i]![key] is { } v) target[slot]![key] = v.DeepClone();
                        if (charge && periods[i]!["grid_charging"] is { } gc) target[slot]!["grid_charge"] = gc.DeepClone();
                    }
                }
            }
        }
    }
}

/// <summary>Runs the real application against the test database (recreated empty on every run).</summary>
public class AppFixture : WebApplicationFactory<Program>
{
    public readonly CapturingOtpSender Otp = new();
    public readonly FakeDevices Devices = new();
    public readonly ScriptedLlm Llm = new();
    public readonly FakeWeather Weather = new();
    private int _counter;
    private readonly Dictionary<string, string?> _settings = [];

    /// <summary>
    /// Another host on the database the main fixture prepared (it does not wipe it), with its own settings, such as the
    /// instances of a multi-instance test. <paramref name="firstNumber"/> keeps its serials and users apart.
    /// </summary>
    protected AppFixture(Dictionary<string, string?> settings, int firstNumber)
    {
        _settings = settings;
        _counter = firstNumber;
    }

    public AppFixture()
    {
        var cs = Environment.GetEnvironmentVariable("GGP_ConnectionStrings__Main")
            ?? throw new InvalidOperationException("Run the tests with .\\run.ps1 -Test (it points GGP_ConnectionStrings__Main at the test database).");
        var db = new NpgsqlConnectionStringBuilder(cs).Database ?? "";
        // Safety: this fixture wipes the database it is given.
        if (!db.EndsWith("_test", StringComparison.Ordinal)) throw new InvalidOperationException($"Refusing to run tests against database '{db}': its name must end with _test.");
        using (var c = new NpgsqlConnection(cs))
        {
            c.Open();
            c.Execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
        }
        foreach (var (k, v) in new Dictionary<string, string>
                 {
                     ["GGP_Security__JwtSigningKey"] = "test-signing-key-test-signing-key-test-signing-key",
                     ["GGP_Mqtt__Enabled"] = "false",
                     ["GGP_RateLimits__AuthPerMinute"] = "100000",
                     ["GGP_RateLimits__ApiPerMinute"] = "100000",
                     ["GGP_Security__OtpResendSeconds"] = "0",
                     ["GGP_Security__OtpMaxPerHour"] = "1000",
                     ["GGP_Commands__MinIntervalSeconds"] = "0",
                     ["GGP_Commands__AckTimeoutSeconds"] = "1",
                     ["GGP_Commands__ReadbackTimeoutSeconds"] = "2",
                     ["GGP_Storage__Root"] = Path.Combine(Path.GetTempPath(), "ggpower-test-storage"),
                     ["GGP_Chat__MaxQuestionsPerConversation"] = "2",
                     ["GGP_Chat__MaxQuestionsPerUserPerDay"] = "3",
                 })
            Environment.SetEnvironmentVariable(k, v);
    }

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment("Testing");
        if (_settings.Count > 0) builder.ConfigureAppConfiguration((_, c) => c.AddInMemoryCollection(_settings));
        builder.ConfigureTestServices(s =>
        {
            s.RemoveAll<IOtpSender>();
            s.AddSingleton<IOtpSender>(Otp);
            s.RemoveAll<IMqttPublisher>();
            s.AddSingleton<IMqttPublisher>(Devices);
            s.RemoveAll<GgPower.Api.Chat.ILlmClient>();
            s.AddSingleton<GgPower.Api.Chat.ILlmClient>(Llm);
            s.RemoveAll<GgPower.Api.Chat.IWeatherSource>();
            s.AddSingleton<GgPower.Api.Chat.IWeatherSource>(Weather);
        });
    }

    public T Service<T>() where T : notnull
    {
        Devices.Services ??= Services;
        return Services.GetRequiredService<T>();
    }

    public string Unique(string prefix) => $"{prefix}{Interlocked.Increment(ref _counter):D4}";

    // ----- HTTP helpers -----

    public async Task<(HttpStatusCode Status, JsonNode? Body, HttpResponseMessage Raw)> Send(
        HttpMethod method, string path, object? body = null, string? token = null, Dictionary<string, string>? headers = null)
    {
        Devices.Services ??= Services;
        var client = CreateClient();
        var req = new HttpRequestMessage(method, path);
        if (token is not null) req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        if (headers is not null) foreach (var h in headers) req.Headers.TryAddWithoutValidation(h.Key, h.Value);
        if (body is byte[] raw) req.Content = new ByteArrayContent(raw);
        else if (body is not null) req.Content = new StringContent(body as string ?? JsonSerializer.Serialize(body), Encoding.UTF8, "application/json");
        var res = await client.SendAsync(req);
        var text = await res.Content.ReadAsStringAsync();
        JsonNode? node = null;
        if (text.Length > 0) { try { node = JsonNode.Parse(text); } catch (JsonException) { } }
        return (res.StatusCode, node, res);
    }

    public Task<(HttpStatusCode Status, JsonNode? Body, HttpResponseMessage Raw)> Get(string path, string? token = null) => Send(HttpMethod.Get, path, null, token);
    public Task<(HttpStatusCode Status, JsonNode? Body, HttpResponseMessage Raw)> Post(string path, object? body, string? token = null, Dictionary<string, string>? headers = null) =>
        Send(HttpMethod.Post, path, body ?? new { }, token, headers);

    public sealed record User(string Email, string Phone, string Password, string Access, string Refresh);

    public async Task<User> NewUser(string? role = null, bool writeAdmin = false)
    {
        var n = Interlocked.Increment(ref _counter);
        var phone = $"09{n:D8}";
        var email = $"user{n}@test.example";
        const string password = "matkhau123";
        var (s, _, _) = await Post("/api/v1/auth/register", new { name = $"User {n}", email, phone, password });
        Assert.Equal(HttpStatusCode.Accepted, s);
        var (s2, tokens, _) = await Post("/api/v1/auth/register/verify", new { phone, code = Otp.Codes[phone] });
        Assert.Equal(HttpStatusCode.OK, s2);
        if (role is not null || writeAdmin)
        {
            await using var c = await Service<Db>().OpenAsync();
            await c.ExecuteAsync("UPDATE users SET role = COALESCE(@role, role), write_admin = @writeAdmin WHERE email = @email", new { role, writeAdmin, email });
            (_, tokens, _) = await Post("/api/v1/auth/login", new { account = email, password });
        }
        return new User(email, phone, password, (string)tokens!["access_token"]!, (string)tokens["refresh_token"]!);
    }

    // ----- device helpers -----

    /// <summary>Delivers one device message and waits until its database work (status, alarms…) is done.</summary>
    public async Task Feed(string serial, string kind, object payload, bool retained = false)
    {
        await Service<IngestPipeline>().HandleAsync($"solar/ggp/inverter/{serial}/up/{kind}",
            Encoding.UTF8.GetBytes(payload as string ?? JsonSerializer.Serialize(payload)), CancellationToken.None, retained);
        await Service<DeviceEventQueue>().DrainAsync();
    }

    public static object Telemetry(string serial, double pv = 1500, double grid = 200, double load = 900, double battFlow = -800, double soc = 60, long? ts = null) => new
    {
        sn = serial, ts = ts ?? DateTimeOffset.UtcNow.ToUnixTimeSeconds(), inv_status = 3, inv_temp = 41.5,
        pv = new { v_pv1 = 320.0, i_pv1 = 4.0, p_pv1 = pv, p_total = pv },
        grid = new { v_ac = 230.0, freq = 50.0, p_active = grid },
        load = new { v_out = 230.0, p_active = load },
        battery = new { p_flow = battFlow, soc },
        energy = new { yield_today_kwh = 7.5 },
    };

    /// <summary>A device that is online, has reported its settings and has sent one sample.</summary>
    public async Task<string> NewDevice(FakeDevices.Behaviour behaviour = FakeDevices.Behaviour.Normal)
    {
        var serial = Unique("TEST-DEV-");
        Devices.Mode[serial] = behaviour;
        Devices.State[serial] = FakeDevices.InitialState(serial);
        await Feed(serial, "status", new { sn = serial, state = "online", model = "Test Inverter", fw_ver = "V1", logger_fw = "1.0.16", inverter_comm = "ok" });
        await Feed(serial, "config/state", Devices.State[serial].ToJsonString());
        await Feed(serial, "telemetry", Telemetry(serial));
        return serial;
    }

    public async Task<string> NewProject(User owner, string serial)
    {
        var (s, body, _) = await Post("/api/v1/projects", new
        {
            serial, name = "Test " + serial, site_type = "home", address = "Somewhere", time_zone = "Asia/Ho_Chi_Minh", installed_on = "2026-01-01", pv_kwp = 5,
        }, owner.Access);
        Assert.Equal(HttpStatusCode.Created, s);
        return (string)body!["project_id"]!;
    }

    public async Task Allow(string serial)
    {
        await using var c = await Service<Db>().OpenAsync();
        await c.ExecuteAsync("INSERT INTO config_allowlist (serial) VALUES (@serial) ON CONFLICT DO NOTHING", new { serial });
    }

    public async Task<JsonNode> Command(User user, string serial, object request, string? key = null)
    {
        var (s, body, _) = await Post($"/api/v1/devices/{serial}/config-commands", request, user.Access, new() { ["Idempotency-Key"] = key ?? Guid.NewGuid().ToString() });
        Assert.True(s == HttpStatusCode.Accepted, $"expected 202, got {(int)s}: {body}");
        var id = (string)body!["command_id"]!;
        for (var i = 0; i < 100; i++)
        {
            var (_, cmd, _) = await Get($"/api/v1/devices/{serial}/config-commands/{id}", user.Access);
            if ((string)cmd!["status"]! is "applied" or "reverted" or "failed") return cmd;
            await Task.Delay(150);
        }
        throw new TimeoutException("command did not reach a final state");
    }

    public async Task<string> Version(User user, string serial) =>
        (string)(await Get($"/api/v1/devices/{serial}/config", user.Access)).Body!["version"]!;
}

[CollectionDefinition("app")]
public class AppCollection : ICollectionFixture<AppFixture>;

[Collection("app")]
public class AuthTests(AppFixture app)
{
    [Fact]
    public async Task RegisterVerifyAndReadProfile()
    {
        var u = await app.NewUser();
        var (s, me, _) = await app.Get("/api/v1/me", u.Access);
        Assert.Equal(HttpStatusCode.OK, s);
        Assert.Equal(u.Email, (string)me!["email"]!);
        Assert.Equal("owner", (string)me["role"]!);
        Assert.Equal(HttpStatusCode.Unauthorized, (await app.Get("/api/v1/me")).Status);
        Assert.Equal(HttpStatusCode.Unauthorized, (await app.Get("/api/v1/me", "not.a.token")).Status);
    }

    [Fact]
    public async Task UnverifiedAccountCannotSignInAndDuplicatesAreRejected()
    {
        var phone = "0987000001";
        var email = "pending@test.example";
        await app.Post("/api/v1/auth/register", new { name = "P", email, phone, password = "matkhau123" });
        var (s, body, _) = await app.Post("/api/v1/auth/login", new { account = email, password = "matkhau123" });
        Assert.Equal(HttpStatusCode.Forbidden, s);
        Assert.Equal("ACCOUNT_NOT_VERIFIED", (string)body!["code"]!);

        var u = await app.NewUser();
        (s, body, _) = await app.Post("/api/v1/auth/register", new { name = "X", email = u.Email, phone = "0987000002", password = "matkhau123" });
        Assert.Equal((HttpStatusCode.Conflict, "EMAIL_TAKEN"), (s, (string)body!["code"]!));
        (s, body, _) = await app.Post("/api/v1/auth/register", new { name = "X", email = "other@test.example", phone = u.Phone, password = "matkhau123" });
        Assert.Equal((HttpStatusCode.Conflict, "PHONE_TAKEN"), (s, (string)body!["code"]!));
        (s, _, _) = await app.Post("/api/v1/auth/register", new { name = "X", email = "weak@test.example", phone = "0987000003", password = "weak" });
        Assert.Equal(HttpStatusCode.BadRequest, s);
    }

    [Fact]
    public async Task WrongCodeCountsDownThenLocks()
    {
        var phone = "0987000010";
        await app.Post("/api/v1/auth/register", new { name = "C", email = "codes@test.example", phone, password = "matkhau123" });
        var right = app.Otp.Codes[phone];
        var wrong = right == "000000" ? "111111" : "000000";
        for (var left = 4; left >= 1; left--)
        {
            var (s, body, _) = await app.Post("/api/v1/auth/register/verify", new { phone, code = wrong });
            Assert.Equal(HttpStatusCode.BadRequest, s);
            Assert.Equal(left, (int)body!["attempts_left"]!);
        }
        Assert.Equal(HttpStatusCode.Locked, (await app.Post("/api/v1/auth/register/verify", new { phone, code = wrong })).Status);
        // even the right code is refused once the code is locked
        Assert.Equal(HttpStatusCode.Locked, (await app.Post("/api/v1/auth/register/verify", new { phone, code = right })).Status);
    }

    [Fact]
    public async Task AccountLocksAfterRepeatedWrongPasswords()
    {
        var u = await app.NewUser();
        for (var i = 0; i < 5; i++)
            Assert.Equal(HttpStatusCode.Unauthorized, (await app.Post("/api/v1/auth/login", new { account = u.Email, password = "wrongpass1" })).Status);
        var (s, body, _) = await app.Post("/api/v1/auth/login", new { account = u.Email, password = u.Password });
        Assert.Equal((HttpStatusCode.TooManyRequests, "ACCOUNT_LOCKED"), (s, (string)body!["code"]!));
    }

    [Fact]
    public async Task RefreshRotatesAndReuseRevokesTheSession()
    {
        var u = await app.NewUser();
        var (s, next, _) = await app.Post("/api/v1/auth/refresh", new { refresh_token = u.Refresh });
        Assert.Equal(HttpStatusCode.OK, s);
        var newRefresh = (string)next!["refresh_token"]!;
        Assert.NotEqual(u.Refresh, newRefresh);
        // the old token is presented again: treated as theft, the whole session ends
        Assert.Equal(HttpStatusCode.Unauthorized, (await app.Post("/api/v1/auth/refresh", new { refresh_token = u.Refresh })).Status);
        Assert.Equal(HttpStatusCode.Unauthorized, (await app.Post("/api/v1/auth/refresh", new { refresh_token = newRefresh })).Status);
    }

    [Fact]
    public async Task LogoutEndsTheSession()
    {
        var u = await app.NewUser();
        Assert.Equal(HttpStatusCode.NoContent, (await app.Post("/api/v1/auth/logout", new { refresh_token = u.Refresh })).Status);
        Assert.Equal(HttpStatusCode.Unauthorized, (await app.Post("/api/v1/auth/refresh", new { refresh_token = u.Refresh })).Status);
    }

    [Fact]
    public async Task PasswordResetIsSingleUseAndSignsOtherSessionsOut()
    {
        var u = await app.NewUser();
        Assert.Equal(HttpStatusCode.Accepted, (await app.Post("/api/v1/auth/password/forgot", new { account = u.Email })).Status);
        // unknown accounts get the same answer
        Assert.Equal(HttpStatusCode.Accepted, (await app.Post("/api/v1/auth/password/forgot", new { account = "nobody@test.example" })).Status);
        var (s, body, _) = await app.Post("/api/v1/auth/password/verify", new { account = u.Email, code = app.Otp.Codes[u.Email] });
        Assert.Equal(HttpStatusCode.OK, s);
        var token = (string)body!["reset_token"]!;
        Assert.Equal(HttpStatusCode.NoContent, (await app.Post("/api/v1/auth/password/reset", new { reset_token = token, new_password = "matkhau456" })).Status);
        Assert.Equal(HttpStatusCode.Unauthorized, (await app.Post("/api/v1/auth/password/reset", new { reset_token = token, new_password = "matkhau789" })).Status);
        Assert.Equal(HttpStatusCode.Unauthorized, (await app.Post("/api/v1/auth/refresh", new { refresh_token = u.Refresh })).Status);
        Assert.Equal(HttpStatusCode.Unauthorized, (await app.Post("/api/v1/auth/login", new { account = u.Email, password = u.Password })).Status);
        Assert.Equal(HttpStatusCode.OK, (await app.Post("/api/v1/auth/login", new { account = u.Email, password = "matkhau456" })).Status);
    }
}

[Collection("app")]
public class AccessTests(AppFixture app)
{
    [Fact]
    public async Task AnotherAccountSeesNothingOfAProject()
    {
        var owner = await app.NewUser();
        var other = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);
        foreach (var path in new[] { "overview", "devices", "telemetry", "alerts", "warranty", "stats?period=day&anchor=2026-10-06", "day-curves?date=2026-10-06", "history?range=hour&fields=pv_power" })
        {
            Assert.Equal(HttpStatusCode.OK, (await app.Get($"/api/v1/projects/{project}/{path}", owner.Access)).Status);
            Assert.Equal(HttpStatusCode.NotFound, (await app.Get($"/api/v1/projects/{project}/{path}", other.Access)).Status);
            Assert.Equal(HttpStatusCode.Unauthorized, (await app.Get($"/api/v1/projects/{project}/{path}")).Status);
        }
        Assert.Equal(HttpStatusCode.NotFound, (await app.Get($"/api/v1/devices/{serial}/config", other.Access)).Status);
        var (s, _, _) = await app.Post($"/api/v1/devices/{serial}/config-commands", new { group = "operating_mode", based_on_version = "x", mode = "self_consumption" },
            other.Access, new() { ["Idempotency-Key"] = Guid.NewGuid().ToString() });
        Assert.Equal(HttpStatusCode.NotFound, s);
        Assert.Empty((await app.Get("/api/v1/projects", other.Access)).Body!.AsArray());
        Assert.Equal(HttpStatusCode.NotFound, (await app.Send(HttpMethod.Delete, $"/api/v1/projects/{project}", null, other.Access)).Status);
    }

    [Fact]
    public async Task ADeviceBelongsToOneProjectUntilThatProjectIsDeleted()
    {
        var owner = await app.NewUser();
        var other = await app.NewUser();
        var serial = await app.NewDevice();
        Assert.Equal(HttpStatusCode.NotFound, (await app.Post("/api/v1/projects/check-serial", new { serial = "NO-SUCH-DEVICE" }, owner.Access)).Status);
        Assert.Equal(HttpStatusCode.OK, (await app.Post("/api/v1/projects/check-serial", new { serial }, other.Access)).Status);
        var project = await app.NewProject(owner, serial);
        var (s, body, _) = await app.Post("/api/v1/projects/check-serial", new { serial }, other.Access);
        Assert.Equal((HttpStatusCode.Conflict, "SERIAL_OWNED"), (s, (string)body!["code"]!));
        (s, body, _) = await app.Post("/api/v1/projects", new { serial, name = "Steal", site_type = "home", address = "x", installed_on = "2026-01-01", pv_kwp = 5 }, other.Access);
        Assert.Equal((HttpStatusCode.Conflict, "SERIAL_OWNED"), (s, (string)body!["code"]!));

        Assert.Equal(HttpStatusCode.NoContent, (await app.Send(HttpMethod.Delete, $"/api/v1/projects/{project}", null, owner.Access)).Status);
        Assert.Equal(HttpStatusCode.OK, (await app.Post("/api/v1/projects/check-serial", new { serial }, other.Access)).Status);
    }

    [Fact]
    public async Task AdminAndStaffEndpointsNeedTheirRoles()
    {
        var owner = await app.NewUser();
        Assert.Equal(HttpStatusCode.Forbidden, (await app.Get("/api/v1/admin/config-write-switch", owner.Access)).Status);
        Assert.Equal(HttpStatusCode.Forbidden, (await app.Get("/api/v1/admin/tickets", owner.Access)).Status);
        // a technician without the write-admin flag may handle tickets but not the write switch
        var tech = await app.NewUser(role: "ggp_technician");
        Assert.Equal(HttpStatusCode.OK, (await app.Get("/api/v1/admin/tickets", tech.Access)).Status);
        Assert.Equal(HttpStatusCode.Forbidden, (await app.Get("/api/v1/admin/config-write-switch", tech.Access)).Status);
        var admin = await app.NewUser(role: "ggp_technician", writeAdmin: true);
        Assert.Equal(HttpStatusCode.OK, (await app.Get("/api/v1/admin/config-write-switch", admin.Access)).Status);
    }
}

[Collection("app")]
public class IngestTests(AppFixture app)
{
    [Fact]
    public async Task TelemetryReachesTheProjectListWithAppSignConventions()
    {
        var owner = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);
        await app.Feed(serial, "telemetry", AppFixture.Telemetry(serial, pv: 2400, grid: -300, load: 1000, battFlow: -1100, soc: 72));

        var list = (await app.Get("/api/v1/projects", owner.Access)).Body!.AsArray();
        var p = Assert.Single(list)!;
        Assert.Equal("online", (string)p["status"]!);
        Assert.Equal(2.4, (double)p["pv_power_kw"]!);
        Assert.Equal(72, (int)p["battery_soc_percent"]!);

        var energy = (await app.Get($"/api/v1/projects/{project}/overview", owner.Access)).Body!["energy"]!;
        Assert.Equal(-0.3, (double)energy["grid_kw"]!);      // exporting stays negative
        Assert.Equal(1.1, (double)energy["battery_kw"]!);    // charging is positive for the app
        Assert.Equal("battery_priority", (string)(await app.Get($"/api/v1/projects/{project}/overview", owner.Access)).Body!["mode"]!);
    }

    [Fact]
    public async Task MissingValuesStayUnknown()
    {
        var owner = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);
        await app.Feed(serial, "telemetry", new { sn = serial, ts = DateTimeOffset.UtcNow.ToUnixTimeSeconds(), grid = new { v_ac = 229.5 } });
        var t = (await app.Get($"/api/v1/projects/{project}/telemetry", owner.Access)).Body!["telemetry"]!;
        Assert.Equal(229.5, (double)t["grid_v"]!);
        Assert.Null(t["pv_w"]);
        Assert.Null(t["soc_percent"]);
    }

    [Fact]
    public async Task DataloggerLinkAndBatteryLimitsReachTheDeviceScreens()
    {
        var owner = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);
        await app.Feed(serial, "status", new { sn = serial, state = "online", logger_fw = "1.0.17", ip_addr = "192.168.1.23", signal_rssi = -61 });

        // Current firmware: no own serial, no link type → serial unknown, link is the Wi-Fi the product ships with.
        var logger = (await app.Get($"/api/v1/projects/{project}/devices", owner.Access)).Body!.AsArray()[0]!["datalogger"]!;
        Assert.Null(logger["serial"]);
        Assert.Equal("wifi", (string)logger["connection"]!);
        Assert.Equal(-61, (int)logger["rssi_dbm"]!);
        Assert.Equal("192.168.1.23", (string)logger["ip"]!);
        Assert.Equal("1.0.17", (string)logger["firmware"]!);
        Assert.True((bool)logger["online"]!);

        // Newer firmware sends them; an unknown link type is ignored.
        await app.Feed(serial, "status", new { sn = serial, state = "online", logger_sn = "GGW-25031501", conn_type = "ethernet" });
        var frame = (await app.Get($"/api/v1/projects/{project}/telemetry", owner.Access)).Body!;
        Assert.Equal("GGW-25031501", (string)frame["datalogger"]!["serial"]!);
        Assert.Equal("ethernet", (string)frame["datalogger"]!["connection"]!);
        Assert.Equal(-61, (int)frame["datalogger"]!["rssi_dbm"]!);   // kept from the earlier status

        // Battery limits: discharge stops at the backup SOC; the charge limit waits for the firmware.
        await app.Feed(serial, "battery", new { sn = serial, ts = DateTimeOffset.UtcNow.ToUnixTimeSeconds(), soc = 76, voltage = 52.0, current = 3.0, present = true });
        var battery = (await app.Get($"/api/v1/projects/{project}/telemetry", owner.Access)).Body!["battery"]!;
        Assert.Equal(10, (int)battery["discharge_limit_soc_percent"]!);
        Assert.Null(battery["charge_limit_soc_percent"]);
    }

    [Fact]
    public async Task ReplayedBrokerMessagesAreNotASignOfLife()
    {
        var owner = await app.NewUser();
        var serial = app.Unique("TEST-OLD-");
        // what the broker replays from its retained store for a device that is switched off
        await app.Feed(serial, "status", new { sn = serial, state = "online", model = "Old" }, retained: true);
        await app.Feed(serial, "config/state", FakeDevices.InitialState(serial).ToJsonString(), retained: true);
        // LastSeen of a never-seen device is the epoch, so it is not reachable
        var project = await app.NewProject(owner, serial);
        Assert.Equal("offline", (string)(await app.Get($"/api/v1/projects/{project}/overview", owner.Access)).Body!["status"]!);
        // a live message brings it online
        await app.Feed(serial, "telemetry", AppFixture.Telemetry(serial));
        Assert.Equal("online", (string)(await app.Get($"/api/v1/projects/{project}/overview", owner.Access)).Body!["status"]!);
        // last will from the broker takes it offline at once
        await app.Feed(serial, "status", new { sn = serial, state = "offline", reason = "connection_lost" });
        Assert.Equal("offline", (string)(await app.Get($"/api/v1/projects/{project}/overview", owner.Access)).Body!["status"]!);
    }

    [Fact]
    public async Task HistoryReturnsEveryFieldOfADeviceInOneCall()
    {
        var owner = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);
        // exactly what the app sends: all inverter fields, then all battery fields
        var (s, body, _) = await app.Get($"/api/v1/projects/{project}/history?range=day&fields=pv_power,load_power,temp_dcac,grid_voltage,grid_frequency", owner.Access);
        Assert.Equal(HttpStatusCode.OK, s);
        var values = body!["values"]!.AsObject();
        Assert.Equal(5, values.Count);
        Assert.All(values, kv => Assert.Equal(97, kv.Value!.AsArray().Count));     // 24 h in 15-minute steps, both ends
        (s, body, _) = await app.Get($"/api/v1/projects/{project}/history?range=hour&fields=soc,pack_current,battery_temp,cell_delta", owner.Access);
        Assert.Equal(HttpStatusCode.OK, s);
        Assert.Equal(61, body!["values"]!["soc"]!.AsArray().Count);
        Assert.All(body["values"]!["cell_delta"]!.AsArray(), v => Assert.Null(v));   // not published by the datalogger
        Assert.Equal(169, (await app.Get($"/api/v1/projects/{project}/history?range=week&fields=pv_power", owner.Access)).Body!["values"]!["pv_power"]!.AsArray().Count);
        Assert.Equal(HttpStatusCode.BadRequest, (await app.Get($"/api/v1/projects/{project}/history?range=day&fields=pv_power,no_such_field", owner.Access)).Status);
        Assert.Equal(HttpStatusCode.BadRequest, (await app.Get($"/api/v1/projects/{project}/history?range=year&fields=pv_power", owner.Access)).Status);
    }

    [Fact]
    public async Task GarbageIsIgnored()
    {
        var pipeline = app.Service<IngestPipeline>();
        var before = pipeline.Rejected;
        await pipeline.HandleAsync("solar/ggp/inverter/TEST-X/up/telemetry", Encoding.UTF8.GetBytes("{not json"), CancellationToken.None);
        await pipeline.HandleAsync("solar/other-tenant/inverter/TEST-X/up/telemetry", Encoding.UTF8.GetBytes("{}"), CancellationToken.None);
        await pipeline.HandleAsync("solar/ggp/inverter/bad serial/up/telemetry", Encoding.UTF8.GetBytes("{}"), CancellationToken.None);
        await pipeline.HandleAsync("solar/ggp/inverter/TEST-X/up/telemetry", new byte[32 * 1024], CancellationToken.None);
        Assert.Equal(before + 4, pipeline.Rejected);
        Assert.Null(app.Service<LiveStateCache>().Get("bad serial"));
    }

    [Fact]
    public async Task SamplesAreStoredAndRolledUpIntoStatistics()
    {
        var owner = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);
        var now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        // one minute of 3.6 kW PV, 1.2 kW exported, 1.0 kW into the battery, 1.4 kW load
        for (var i = 12; i >= 1; i--)
            await app.Feed(serial, "telemetry", AppFixture.Telemetry(serial, pv: 3600, grid: -1200, load: 1400, battFlow: -1000, soc: 50, ts: now - i * 5));

        await using var c = await app.Service<Db>().OpenAsync();
        for (var i = 0; i < 40 && await c.ExecuteScalarAsync<int>("SELECT count(*) FROM telemetry_raw WHERE serial = @serial", new { serial }) < 12; i++) await Task.Delay(200);
        Assert.True(await c.ExecuteScalarAsync<int>("SELECT count(*) FROM telemetry_raw WHERE serial = @serial", new { serial }) >= 12);

        await app.Service<RollupService>().RunOnceAsync(DateTimeOffset.UtcNow, CancellationToken.None);
        var totals = await c.QuerySingleAsync<(double Pv, double Exp, double Chg, double Self, double PvExp, double PvChg)>(
            "SELECT sum(pv_kwh), sum(export_kwh), sum(charge_kwh), sum(pv_self_kwh), sum(pv_export_kwh), sum(pv_charge_kwh) FROM rollup_15m WHERE serial = @serial", new { serial });
        // 12 samples x 5 s x 3600 W = 0.06 kWh (the device's first sample from NewDevice adds a little)
        Assert.InRange(totals.Pv, 0.055, 0.075);
        Assert.InRange(totals.Exp, 0.018, 0.022);
        // PV is fully accounted for: self-use + battery + export
        Assert.Equal(totals.Pv, totals.Self + totals.PvExp + totals.PvChg, 6);

        var today = TimeZoneInfo.ConvertTime(DateTimeOffset.UtcNow, TimeZoneInfo.FindSystemTimeZoneById("Asia/Ho_Chi_Minh")).ToString("yyyy-MM-dd");
        var stats = (await app.Get($"/api/v1/projects/{project}/stats?period=day&anchor={today}", owner.Access)).Body!;
        Assert.Equal(12, stats["energy_buckets"]!.AsArray().Count);
        Assert.True((double)stats["summary"]!["consumption_kwh"]! > 0);
        Assert.True(stats["energy_buckets"]!.AsArray().Sum(b => (double)b!["pv_kwh"]!) > 0.05);
        var curves = (await app.Get($"/api/v1/projects/{project}/day-curves?date={today}", owner.Access)).Body!;
        Assert.NotEmpty(curves["power"]!.AsArray());

        // Home screen: charged today by source (1 kW into the battery, all of it PV surplus), CO₂ from today's yield.
        var overview = (await app.Get($"/api/v1/projects/{project}/overview", owner.Access)).Body!;
        Assert.Equal("Test Inverter", (string)overview["model"]!);
        Assert.Equal(5.1, (double)overview["co2_today_kg"]!);   // 7.5 kWh × 0.6766
        var charged = overview["battery_charged_today"]!;
        Assert.InRange((double)charged["from_pv_kwh"]!, 0.01, 0.03);
        Assert.Equal(0, (double)charged["from_grid_kwh"]!);
        // The roll-up copied the same figure into the live state, so the realtime frame carries it too.
        var frame = (await app.Get($"/api/v1/projects/{project}/telemetry", owner.Access)).Body!;
        Assert.Equal((double)charged["from_pv_kwh"]!, (double)frame["battery_charged_today"]!["from_pv_kwh"]!);
        Assert.Equal(5.1, (double)frame["co2_today_kg"]!);

        var closed = await app.Get($"/api/v1/projects/{project}/stats?period=day&anchor=2026-01-01", owner.Access);
        Assert.Contains("immutable", closed.Raw.Headers.CacheControl!.ToString());
        var etag = closed.Raw.Headers.ETag!.Tag;
        var again = await app.Send(HttpMethod.Get, $"/api/v1/projects/{project}/stats?period=day&anchor=2026-01-01", null, owner.Access, new() { ["If-None-Match"] = etag });
        Assert.Equal(HttpStatusCode.NotModified, again.Status);
    }
}

[Collection("app")]
public class AlarmTests(AppFixture app)
{
    private static object Alarm(string serial, string id, string state, long ts, string code = "F024", string severity = "critical") => new
    {
        alarm_id = id, sn = serial, ts, event_state = state, error_code = code, severity, title = "Mất kết nối lưới điện", description = "D",
    };

    [Fact]
    public async Task LifecycleAndDeduplication()
    {
        var owner = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);
        var path = $"/api/v1/projects/{project}";
        var t0 = DateTimeOffset.UtcNow.ToUnixTimeSeconds() - 100;

        await app.Feed(serial, "alarm", Alarm(serial, "ALM-1", "triggered", t0));
        var alerts = (await app.Get(path + "/alerts", owner.Access)).Body!.AsArray();
        Assert.Single(alerts);
        Assert.Equal("error", (string)alerts[0]!["severity"]!);
        var overview = (await app.Get(path + "/overview", owner.Access)).Body!;
        Assert.Equal("error", (string)overview["status"]!);
        Assert.Equal(1, (int)overview["unresolved_alerts"]!);

        // the faults the inverter reports right now are part of the live snapshot
        var active = (await app.Get(path + "/telemetry", owner.Access)).Body!["active_errors"]!.AsArray();
        Assert.Equal("F024", (string)Assert.Single(active)!["code"]!);

        // redelivery, and a restarted datalogger reporting the same fault under a new id: still one alert
        await app.Feed(serial, "alarm", Alarm(serial, "ALM-1", "triggered", t0));
        await app.Feed(serial, "alarm", Alarm(serial, "ALM-7", "triggered", t0 + 20));
        Assert.Single((await app.Get(path + "/alerts", owner.Access)).Body!.AsArray());

        // a second, different fault
        await app.Feed(serial, "alarm", Alarm(serial, "ALM-2", "triggered", t0 + 30, code: "F058", severity: "warning"));
        Assert.Equal(2, (await app.Get(path + "/telemetry", owner.Access)).Body!["active_errors"]!.AsArray().Count);

        await app.Feed(serial, "alarm", Alarm(serial, "ALM-1", "cleared", t0 + 40));
        alerts = (await app.Get(path + "/alerts", owner.Access)).Body!.AsArray();
        Assert.NotNull(alerts.Single(a => (string)a!["code"]! == "F024")!["ended_at_utc"]);
        Assert.Equal("warning", (string)(await app.Get(path + "/overview", owner.Access)).Body!["status"]!);
        Assert.Equal("F058", (string)Assert.Single((await app.Get(path + "/telemetry", owner.Access)).Body!["active_errors"]!.AsArray())!["code"]!);

        // the fault comes back shortly after clearing: the same alert continues and counts, no second alert
        await app.Feed(serial, "alarm", Alarm(serial, "ALM-1", "triggered", t0 + 60));
        alerts = (await app.Get(path + "/alerts", owner.Access)).Body!.AsArray();
        var f024 = alerts.Single(a => (string)a!["code"]! == "F024")!;
        Assert.Null(f024["ended_at_utc"]);
        Assert.Equal(2, (int)f024["occurrences"]!);
        Assert.Equal(2, (await app.Get(path + "/telemetry", owner.Access)).Body!["active_errors"]!.AsArray().Count);
        // ... however often it flaps
        for (var i = 0; i < 3; i++)
        {
            await app.Feed(serial, "alarm", Alarm(serial, $"ALM-F{i}", "cleared", t0 + 61 + i * 2));
            await app.Feed(serial, "alarm", Alarm(serial, $"ALM-F{i}", "triggered", t0 + 62 + i * 2));
        }
        alerts = (await app.Get(path + "/alerts", owner.Access)).Body!.AsArray();
        Assert.Equal(5, (int)alerts.Single(a => (string)a!["code"]! == "F024")!["occurrences"]!);

        // once the user has marked it as dealt with, a new occurrence is a new alert
        var dealt = (string)alerts.Single(a => (string)a!["code"]! == "F024")!["id"]!;
        await app.Send(HttpMethod.Put, $"{path}/alerts/{dealt}/resolved", new { resolved = true }, owner.Access);
        await app.Feed(serial, "alarm", Alarm(serial, "ALM-1", "cleared", t0 + 70));
        // the device's daily counter restarted as well: the old id returns for a new event and must be kept
        await app.Feed(serial, "alarm", Alarm(serial, "ALM-1", "triggered", t0 + 80));
        alerts = (await app.Get(path + "/alerts", owner.Access)).Body!.AsArray();
        Assert.Equal(2, alerts.Count(a => (string)a!["code"]! == "F024"));

        // resolving is the user's bookkeeping and does not close the fault
        var id = (string)alerts.First(a => (string)a!["code"]! == "F058")!["id"]!;
        Assert.Equal(HttpStatusCode.NoContent, (await app.Send(HttpMethod.Put, $"{path}/alerts/{id}/resolved", new { resolved = true }, owner.Access)).Status);
        var detail = (await app.Get($"{path}/alerts/{id}", owner.Access)).Body!;
        Assert.True((bool)detail["resolved"]!);
        Assert.Null(detail["ended_at"]);
        Assert.Equal(HttpStatusCode.NotFound, (await app.Get($"{path}/alerts/{Guid.NewGuid()}", owner.Access)).Status);
    }

    [Fact]
    public async Task AFaultThatReturnsMuchLaterIsANewAlert()
    {
        var owner = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);
        var now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        await app.Feed(serial, "alarm", Alarm(serial, "ALM-1", "triggered", now - 3 * 3600));
        await app.Feed(serial, "alarm", Alarm(serial, "ALM-1", "cleared", now - 3 * 3600 + 60));
        await app.Feed(serial, "alarm", Alarm(serial, "ALM-2", "triggered", now - 10));
        var alerts = (await app.Get($"/api/v1/projects/{project}/alerts", owner.Access)).Body!.AsArray();
        Assert.Equal(2, alerts.Count);
        Assert.All(alerts, a => Assert.Equal(1, (int)a!["occurrences"]!));
    }
}

[Collection("app")]
public class FieldSignalTests(AppFixture app)
{
    private static object Tel(string serial, long ts, bool? present, double pv = 1500) => new
    {
        sn = serial, ts, inv_status = 3,
        pv = new { p_total = pv },
        grid = new { p_active = 0.0 },
        load = new { p_active = pv },
        battery = present is { } p ? (object)new { p_flow = -120.0, soc = 100.0, present = p } : new { p_flow = -120.0, soc = 100.0 },
    };

    [Fact]
    public async Task WhetherThereIsABatteryIsWhatTheInverterReports()
    {
        var owner = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);
        var path = $"/api/v1/projects/{project}";
        var now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();

        // a datalogger that does not report it: the question stays open and the data is passed on unchanged
        await app.Feed(serial, "battery", new { sn = serial, ts = now, soc = 100, voltage = 54.4, current = 0.0 });
        var device = (await app.Get(path + "/devices", owner.Access)).Body!.AsArray()[0]!;
        Assert.Null(device["battery_present"]);
        Assert.NotNull(device["battery"]);

        // the inverter says "no battery" although the charger output shows a voltage
        await app.Feed(serial, "telemetry", Tel(serial, now + 1, false));
        await app.Feed(serial, "battery", new { sn = serial, ts = now + 1, soc = 100, voltage = 54.4, current = 0.0, present = false, bms_comm = "fault", battery_type = 6 });
        device = (await app.Get(path + "/devices", owner.Access)).Body!.AsArray()[0]!;
        Assert.False((bool)device["battery_present"]!);
        Assert.Equal("fault", (string)device["bms_comm"]!);
        Assert.Null(device["battery"]);
        var live = (await app.Get(path + "/telemetry", owner.Access)).Body!;
        Assert.False((bool)live["battery_present"]!);
        Assert.Null(live["battery"]);
        Assert.Null(live["telemetry"]!["soc_percent"]);
        Assert.Null(live["telemetry"]!["battery_w"]);
        var overview = (await app.Get(path + "/overview", owner.Access)).Body!;
        Assert.False((bool)overview["battery_present"]!);
        Assert.Null(overview["energy"]!["battery_kw"]);
        Assert.Null(overview["energy"]!["battery_soc_percent"]);

        // the inverter says there is one: listed, however low its voltage is
        await app.Feed(serial, "telemetry", Tel(serial, now + 2, true));
        await app.Feed(serial, "battery", new { sn = serial, ts = now + 2, soc = 3, voltage = 4.0, current = 1.0, present = true, bms_comm = "ok" });
        device = (await app.Get(path + "/devices", owner.Access)).Body!.AsArray()[0]!;
        Assert.True((bool)device["battery_present"]!);
        Assert.Equal(3, (int)device["battery"]!["soc_percent"]!);
        Assert.Equal(100, (int)(await app.Get(path + "/telemetry", owner.Access)).Body!["telemetry"]!["soc_percent"]!);

        // what was reported is stored with the samples
        await using var c = await app.Service<Db>().OpenAsync();
        for (var i = 0; i < 40 && await c.ExecuteScalarAsync<int>("SELECT count(*) FROM battery_raw WHERE serial = @serial AND present IS NOT NULL", new { serial }) < 2; i++) await Task.Delay(200);
        Assert.Equal(1, await c.ExecuteScalarAsync<int>("SELECT count(*) FROM battery_raw WHERE serial = @serial AND present = false AND bms_comm = 'fault'", new { serial }));
        Assert.Equal(1, await c.ExecuteScalarAsync<int>("SELECT count(*) FROM telemetry_raw WHERE serial = @serial AND batt_present = false", new { serial }));
    }

    [Fact]
    public async Task SamplesStoredDuringAnOutageFillTheGap()
    {
        var owner = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);
        var now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var liveBefore = (string)(await app.Get($"/api/v1/projects/{project}/telemetry", owner.Access)).Body!["telemetry"]!["ts"]!;
        var versionBefore = await app.Service<ICacheVersions>().VersionAsync(serial);

        // ten samples of 3 kW, two minutes apart, from an hour ago
        var start = now - 3600;
        object Batch(int from, int count, int remaining) => new
        {
            sn = serial, ts = now, interval_s = 120, remaining,
            telemetry = Enumerable.Range(from, count).Select(i => Tel(serial, start + i * 120, false, pv: 3000)).ToArray(),
            battery = Enumerable.Range(from, count).Select(i => new { sn = serial, ts = start + i * 120, voltage = 54.4, current = 0.0, present = false }).ToArray(),
        };
        await app.Feed(serial, "backfill", Batch(0, 5, 5));
        await app.Feed(serial, "backfill", Batch(5, 5, 0));
        await app.Feed(serial, "backfill", Batch(5, 5, 0));   // sent twice: stored once
        // a sample without a usable time is not stored (it is never given the receive time), nor is an empty one
        var rejected = app.Service<IngestPipeline>().Rejected;
        await app.Feed(serial, "backfill", new { sn = serial, ts = now, interval_s = 120, telemetry = new object[] { Tel(serial, now - 40 * 86400, false), new { sn = serial, inv_status = 3 }, new { sn = serial, ts = now - 600 } } });
        Assert.Equal(rejected + 1, app.Service<IngestPipeline>().Rejected);

        await using var c = await app.Service<Db>().OpenAsync();
        for (var i = 0; i < 40 && await c.ExecuteScalarAsync<int>("SELECT count(*) FROM telemetry_raw WHERE serial = @serial AND span_s = 120", new { serial }) < 10; i++) await Task.Delay(200);
        Assert.Equal(10, await c.ExecuteScalarAsync<int>("SELECT count(*) FROM telemetry_raw WHERE serial = @serial AND span_s = 120", new { serial }));
        Assert.Equal(10, await c.ExecuteScalarAsync<int>("SELECT count(*) FROM battery_raw WHERE serial = @serial AND present = false", new { serial }));

        // the live view still shows the current sample, not a stored one
        Assert.Equal(liveBefore, (string)(await app.Get($"/api/v1/projects/{project}/telemetry", owner.Access)).Body!["telemetry"]!["ts"]!);

        // the statistics for that hour are computed again: 10 x 120 s x 3 kW = 1 kWh
        var cutoff = DateTimeOffset.FromUnixTimeSeconds(now - 1800);
        double pv = 0;
        for (var i = 0; i < 40; i++)
        {
            await app.Service<RollupService>().RunOnceAsync(DateTimeOffset.UtcNow, CancellationToken.None);
            pv = await c.ExecuteScalarAsync<double?>("SELECT sum(pv_kwh) FROM rollup_15m WHERE serial = @serial AND bucket < @cutoff", new { serial, cutoff }) ?? 0;
            if (pv > 0.99 && await app.Service<ICacheVersions>().VersionAsync(serial) > versionBefore) break;
            await Task.Delay(250);
        }
        Assert.InRange(pv, 0.99, 1.01);
        Assert.True(await app.Service<ICacheVersions>().VersionAsync(serial) > versionBefore);
        var days = await c.ExecuteScalarAsync<double?>("SELECT sum(pv_kwh) FROM rollup_1d WHERE serial = @serial", new { serial }) ?? 0;
        Assert.True(days > 0.99, $"daily roll-up {days}");
    }

    [Fact]
    public async Task ARoleChangeAppliesEverywhereWithoutSigningInAgain()
    {
        var user = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(user, serial);
        Assert.Equal(HttpStatusCode.Forbidden, (await app.Get("/api/v1/admin/tickets", user.Access)).Status);
        Assert.Equal("owner", (string)(await app.Get("/api/v1/projects", user.Access)).Body!.AsArray()[0]!["role"]!);

        await using (var c = await app.Service<Db>().OpenAsync())
            await c.ExecuteAsync("UPDATE users SET role = 'ggp_technician', write_admin = true WHERE email = @Email", new { user.Email });
        // the answers are cached for a few seconds; drop them instead of waiting
        var payload = user.Access.Split('.')[1].Replace('-', '+').Replace('_', '/');
        payload = payload.PadRight(payload.Length + (4 - payload.Length % 4) % 4, '=');
        var claims = JsonNode.Parse(Convert.FromBase64String(payload))!;
        var cache = app.Service<Microsoft.Extensions.Caching.Memory.IMemoryCache>();
        cache.Remove("sid:" + Guid.Parse((string)claims["sid"]!).ToString("N"));
        cache.Remove($"access:{Guid.Parse((string)claims["sub"]!):N}:{Guid.Parse(project):N}");

        // same token, new rights: staff endpoints, the write switch, and the role shown for the project
        Assert.Equal("owner", (string)claims["role"]!);
        Assert.Equal(HttpStatusCode.OK, (await app.Get("/api/v1/admin/tickets", user.Access)).Status);
        Assert.Equal(HttpStatusCode.OK, (await app.Get("/api/v1/admin/config-write-switch", user.Access)).Status);
        Assert.Equal("ggp_technician", (string)(await app.Get("/api/v1/projects", user.Access)).Body!.AsArray()[0]!["role"]!);
        Assert.Equal("ggp_technician", (string)(await app.Get($"/api/v1/devices/{serial}/config", user.Access)).Body!["write_access"]!["role"]!);
        Assert.Contains("max_grid_export", (await app.Get($"/api/v1/devices/{serial}/config", user.Access)).Body!["write_access"]!["writable_modes"]!.AsArray().Select(x => (string)x!));

        // and taken away again just the same
        await using (var c = await app.Service<Db>().OpenAsync())
            await c.ExecuteAsync("UPDATE users SET role = 'owner', write_admin = false WHERE email = @Email", new { user.Email });
        cache.Remove("sid:" + Guid.Parse((string)claims["sid"]!).ToString("N"));
        Assert.Equal(HttpStatusCode.Forbidden, (await app.Get("/api/v1/admin/tickets", user.Access)).Status);
    }
}

[Collection("app")]
public class CommandTests(AppFixture app)
{
    private static object Slot(int h1, int h2, int soc = 90, int w = 3000) =>
        new { start = new { hour = h1, minute = 0 }, end = new { hour = h2, minute = 0 }, stop_soc_percent = soc, max_power_w = w };

    private static object Strategy(object[] charge, object[]? discharge = null) => new
    {
        id = Guid.NewGuid().ToString(), name = "Hằng ngày", weekdays = new[] { "mon", "tue", "wed", "thu", "fri", "sat", "sun" },
        charge_slots = charge, discharge_slots = discharge ?? [],
    };

    private async Task<(AppFixture.User Owner, string Serial)> Setup(FakeDevices.Behaviour behaviour = FakeDevices.Behaviour.Normal, bool allow = true)
    {
        var owner = await app.NewUser();
        var serial = await app.NewDevice(behaviour);
        await app.NewProject(owner, serial);
        if (allow) await app.Allow(serial);
        return (owner, serial);
    }

    private async Task<(HttpStatusCode, JsonNode?)> Submit(AppFixture.User u, string serial, object request, string? key = null)
    {
        var (s, b, _) = await app.Post($"/api/v1/devices/{serial}/config-commands", request, u.Access, new() { ["Idempotency-Key"] = key ?? Guid.NewGuid().ToString() });
        return (s, b);
    }

    [Fact]
    public async Task AStrategyRunsOnlyOnItsDays()
    {
        var (owner, serial) = await Setup();
        string[] all = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"];
        var todayKey = all[((int)TimeZoneInfo.ConvertTime(DateTimeOffset.UtcNow, TimeZoneInfo.FindSystemTimeZoneById("Asia/Ho_Chi_Minh")).DayOfWeek + 6) % 7];
        var id = Guid.NewGuid().ToString();
        object Days(string[] days) => new { id, name = "A", weekdays = days, charge_slots = new[] { Slot(22, 4) }, discharge_slots = new[] { Slot(18, 21, 20, 5000) } };
        var state = app.Devices.State[serial];

        // a strategy for every day except today, then TOU: nothing may run today
        await app.Command(owner, serial, new { group = "tou_schedule", based_on_version = await app.Version(owner, serial), strategies = new[] { Days(all.Where(d => d != todayKey).ToArray()) } });
        var cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "tou" });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.False((bool)state["charge_schedule"]!["enabled"]!);
        Assert.False((bool)state["discharge_schedule"]!["enabled"]!);
        var config = (await app.Get($"/api/v1/devices/{serial}/config", owner.Access)).Body!;
        Assert.Equal("tou", (string)config["mode"]!);
        Assert.Null(config["today_strategy_id"]);

        // today is added to its days: the slots are loaded at once
        cmd = await app.Command(owner, serial, new { group = "tou_schedule", based_on_version = await app.Version(owner, serial), strategies = new[] { Days(all) } });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.True((bool)state["charge_schedule"]!["enabled"]!);
        Assert.True((bool)state["discharge_schedule"]!["enabled"]!);
        Assert.Equal(id, (string)(await app.Get($"/api/v1/devices/{serial}/config", owner.Access)).Body!["today_strategy_id"]!);

        // today is removed again: both schedules are switched off at once
        cmd = await app.Command(owner, serial, new { group = "tou_schedule", based_on_version = await app.Version(owner, serial), strategies = new[] { Days(all.Where(d => d != todayKey).ToArray()) } });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.False((bool)state["charge_schedule"]!["enabled"]!);
        Assert.False((bool)state["discharge_schedule"]!["enabled"]!);

        // a new day begins and that day belongs to the strategy: the daily reload switches the slots on
        await using var c = await app.Service<Db>().OpenAsync();
        await c.ExecuteAsync("UPDATE tou_strategies SET loaded_on = loaded_on - 1, strategies = replace(strategies::text, '\"weekdays\": [', '\"weekdays\": [\"' || @todayKey || '\", ')::jsonb WHERE serial = @serial", new { serial, todayKey });
        await app.Service<CommandService>().ReloadSchedulesAsync(CancellationToken.None);
        Assert.True((bool)state["charge_schedule"]!["enabled"]!);
        Assert.True((bool)state["discharge_schedule"]!["enabled"]!);
        Assert.Equal("22:00", (string)state["charge_schedule"]!["periods"]![0]!["start_time"]!);

        // the next day does not belong to it: the daily reload switches them off
        await c.ExecuteAsync("UPDATE tou_strategies SET loaded_on = loaded_on - 1, strategies = replace(strategies::text, '\"' || @todayKey || '\", ', '')::jsonb WHERE serial = @serial", new { serial, todayKey });
        app.Service<CommandService>().ForgetReloadAttempts();
        await app.Service<CommandService>().ReloadSchedulesAsync(CancellationToken.None);
        Assert.False((bool)state["charge_schedule"]!["enabled"]!);
        Assert.False((bool)state["discharge_schedule"]!["enabled"]!);
    }

    [Fact]
    public async Task TheDaysOfAStrategyAreWrittenToTheInverter()
    {
        var (owner, serial) = await Setup();
        string[] all = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"];
        var today = ((int)TimeZoneInfo.ConvertTime(DateTimeOffset.UtcNow, TimeZoneInfo.FindSystemTimeZoneById("Asia/Ho_Chi_Minh")).DayOfWeek + 6) % 7;
        // today and the two days after it, given out of order
        var days = new[] { all[(today + 2) % 7], all[today], all[(today + 1) % 7] };
        var id = Guid.NewGuid().ToString();
        object Days(string[] d) => new { id, name = "A", weekdays = d, charge_slots = new[] { Slot(22, 4) }, discharge_slots = new[] { Slot(18, 21, 20, 5000) } };
        var state = app.Devices.State[serial];
        string[] OnDevice() => state["schedule_days"]!.AsArray().Select(x => (string)x!).ToArray();

        await app.Command(owner, serial, new { group = "tou_schedule", based_on_version = await app.Version(owner, serial), strategies = new[] { Days(days) } });
        Assert.Equal(all, OnDevice());   // only stored: not in TOU yet
        var cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "tou" });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.Equal(all.Where(days.Contains), OnDevice());   // Monday-first order, as the inverter reports them
        var change = cmd["changes"]!.AsArray().Single(c => (string)c!["parameter"]! == "schedule_days")!;
        Assert.Equal(7, change["old"]!.AsArray().Count);
        Assert.Equal(3, change["new"]!.AsArray().Count);
        Assert.True((bool)change["readback_matched"]!);

        // one day fewer: only the days are written again, and nothing when they did not change
        var fewer = new[] { all[today], all[(today + 1) % 7] };
        cmd = await app.Command(owner, serial, new { group = "tou_schedule", based_on_version = await app.Version(owner, serial), strategies = new[] { Days(fewer) } });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.Equal(all.Where(fewer.Contains), OnDevice());
        var before = app.Devices.Received.Count(r => r.Serial == serial && r.Action == "SET_SCHEDULE_DAYS");
        cmd = await app.Command(owner, serial, new { group = "tou_schedule", based_on_version = await app.Version(owner, serial), strategies = new[] { new { id, name = "B", weekdays = fewer, charge_slots = new[] { Slot(22, 4) }, discharge_slots = new[] { Slot(18, 21, 20, 5000) } } } });
        Assert.Equal(before, app.Devices.Received.Count(r => r.Serial == serial && r.Action == "SET_SCHEDULE_DAYS"));

        // a datalogger that does not report the days is never sent the command
        var (owner2, old) = await Setup();
        app.Devices.State[old].Remove("schedule_days");
        await app.Feed(old, "config/state", app.Devices.State[old].ToJsonString());
        await app.Command(owner2, old, new { group = "tou_schedule", based_on_version = await app.Version(owner2, old), strategies = new[] { Days(days) } });
        cmd = await app.Command(owner2, old, new { group = "operating_mode", based_on_version = await app.Version(owner2, old), mode = "tou" });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.DoesNotContain(app.Devices.Received, r => r.Serial == old && r.Action == "SET_SCHEDULE_DAYS");
    }

    [Fact]
    public async Task BackupSocIsWrittenAndReadBack()
    {
        var (owner, serial) = await Setup();
        var config = (await app.Get($"/api/v1/devices/{serial}/config", owner.Access)).Body!;
        Assert.Equal(10, (int)config["backup_soc_percent"]!);
        Assert.Contains("backup_soc", config["write_access"]!["writable_groups"]!.AsArray().Select(x => (string)x!));
        Assert.Empty(config["write_access"]!["blocked_groups"]!.AsArray());
        var modeBefore = (string)config["mode"]!;

        var cmd = await app.Command(owner, serial, new { group = "backup_soc", based_on_version = await app.Version(owner, serial), soc_percent = 30 });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.Equal(30, (int)app.Devices.State[serial]["battery_reserve_soc"]!);
        Assert.Equal(40, (int)app.Devices.State[serial]["battery_resume_soc"]!);
        var changes = cmd["changes"]!.AsArray();
        var backup = changes.Single(c => (string)c!["parameter"]! == "backup_soc")!;
        Assert.Equal((10, 30, 30, true), ((int)backup["old"]!, (int)backup["new"]!, (int)backup["readback"]!, (bool)backup["readback_matched"]!));
        var resume = changes.Single(c => (string)c!["parameter"]! == "battery_resume_soc")!;
        Assert.Equal((20, 40), ((int)resume["old"]!, (int)resume["new"]!));
        config = (await app.Get($"/api/v1/devices/{serial}/config", owner.Access)).Body!;
        Assert.Equal(30, (int)config["backup_soc_percent"]!);
        // the operating mode is untouched, and only this one command went to the device
        Assert.Equal(modeBefore, (string)config["mode"]!);
        Assert.Equal("SET_BATTERY_RESERVE", Assert.Single(app.Devices.Received, r => r.Serial == serial).Action);

        // a device that acknowledges but keeps the old value: reported as failed, nothing else is rewritten
        var (owner2, stubborn) = await Setup(FakeDevices.Behaviour.AckButIgnore);
        cmd = await app.Command(owner2, stubborn, new { group = "backup_soc", based_on_version = await app.Version(owner2, stubborn), soc_percent = 50 });
        Assert.Equal("failed", (string)cmd["status"]!);
        Assert.Single(app.Devices.Received, r => r.Serial == stubborn);

        // a datalogger whose firmware cannot write it: blocked, and refused
        var (owner3, old) = await Setup();
        app.Devices.State[old].Remove("battery_reserve_writable");
        await app.Feed(old, "config/state", app.Devices.State[old].ToJsonString());
        config = (await app.Get($"/api/v1/devices/{old}/config", owner3.Access)).Body!;
        Assert.DoesNotContain("backup_soc", config["write_access"]!["writable_groups"]!.AsArray().Select(x => (string)x!));
        Assert.Equal("NOT_SUPPORTED_BY_DEVICE", (string)config["write_access"]!["blocked_groups"]![0]!["reason"]!);
        var (s, body) = await Submit(owner3, old, new { group = "backup_soc", based_on_version = await app.Version(owner3, old), soc_percent = 30 });
        Assert.Equal((HttpStatusCode.Forbidden, "GROUP_NOT_IN_ALLOWLIST"), (s, (string)body!["code"]!));

        // no link to the BMS: blocked with its own reason (the app explains it), and refused; it comes back with the link
        var (owner4, noBms) = await Setup();
        var ts = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        await app.Feed(noBms, "battery", new { sn = noBms, ts, soc = 60, voltage = 52.0, current = 0.0, present = true, bms_comm = "fault" });
        config = (await app.Get($"/api/v1/devices/{noBms}/config", owner4.Access)).Body!;
        Assert.DoesNotContain("backup_soc", config["write_access"]!["writable_groups"]!.AsArray().Select(x => (string)x!));
        Assert.Equal(("backup_soc", "BMS_NOT_CONNECTED"),
            ((string)config["write_access"]!["blocked_groups"]![0]!["group"]!, (string)config["write_access"]!["blocked_groups"]![0]!["reason"]!));
        (s, body) = await Submit(owner4, noBms, new { group = "backup_soc", based_on_version = await app.Version(owner4, noBms), soc_percent = 30 });
        Assert.Equal((HttpStatusCode.Conflict, "BMS_NOT_CONNECTED"), (s, (string)body!["code"]!));
        await app.Feed(noBms, "battery", new { sn = noBms, ts = ts + 1, soc = 60, voltage = 52.0, current = 0.0, present = true, bms_comm = "ok" });
        config = (await app.Get($"/api/v1/devices/{noBms}/config", owner4.Access)).Body!;
        Assert.Contains("backup_soc", config["write_access"]!["writable_groups"]!.AsArray().Select(x => (string)x!));
        Assert.Empty(config["write_access"]!["blocked_groups"]!.AsArray());
    }

    [Fact]
    public async Task DevicesAreReadOnlyUntilAllowed()
    {
        var (owner, serial) = await Setup(allow: false);
        var config = (await app.Get($"/api/v1/devices/{serial}/config", owner.Access)).Body!;
        Assert.Equal("device_read_only", (string)config["write_access"]!["state"]!);
        Assert.Empty(config["write_access"]!["writable_groups"]!.AsArray());
        var (s, body) = await Submit(owner, serial, new { group = "operating_mode", based_on_version = (string)config["version"]!, mode = "self_consumption" });
        Assert.Equal((HttpStatusCode.Conflict, "DEVICE_READ_ONLY"), (s, (string)body!["code"]!));
        Assert.Empty(app.Devices.Received.Where(r => r.Serial == serial));
    }

    [Fact]
    public async Task ModeChangeIsAppliedOnlyAfterReadBack()
    {
        var (owner, serial) = await Setup();
        var cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "self_consumption" });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.Equal("new", (string)cmd["outcome"]!["device_state"]!);
        var change = cmd["changes"]![0]!;
        Assert.Equal(("battery_priority", "self_consumption", "self_consumption", true),
            ((string)change["old"]!, (string)change["new"]!, (string)change["readback"]!, (bool)change["readback_matched"]!));
        Assert.Equal("self_consumption", (string)(await app.Get($"/api/v1/devices/{serial}/config", owner.Access)).Body!["mode"]!);
        // only the PV priority was written; the grid mode was not touched
        var sent = app.Devices.Received.Where(r => r.Serial == serial).ToList();
        Assert.Equal("SET_WORK_MODE", Assert.Single(sent).Action);
        Assert.False(sent[0].Params.ContainsKey("grid_mode"));

        var history = (await app.Get($"/api/v1/devices/{serial}/config-commands?limit=5", owner.Access)).Body!["items"]!.AsArray();
        Assert.Equal("user", (string)history[0]!["initiator"]!["type"]!);
    }

    [Fact]
    public async Task SwitchingOffTimedChargingIsRecordedAsAChange()
    {
        // an inverter on which timed charging was enabled at its own screen, outside TOU
        var (owner, serial) = await Setup();
        app.Devices.State[serial]["charge_schedule"]!["enabled"] = true;
        await app.Feed(serial, "config/state", app.Devices.State[serial].ToJsonString());

        var cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "self_consumption" });
        Assert.Equal("applied", (string)cmd["status"]!);
        var schedule = cmd["changes"]!.AsArray().Single(c => (string)c!["parameter"]! == "charge_schedule_enabled")!;
        Assert.Equal((true, false, false, true), ((bool)schedule["old"]!, (bool)schedule["new"]!, (bool)schedule["readback"]!, (bool)schedule["readback_matched"]!));
        Assert.False((bool)app.Devices.State[serial]["charge_schedule"]!["enabled"]!);

        // going back does not switch it on again: outside TOU it stays off, and no such change is listed
        cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "battery_priority" });
        Assert.DoesNotContain(cmd["changes"]!.AsArray(), c => (string)c!["parameter"]! == "charge_schedule_enabled");
    }

    [Fact]
    public async Task StaleVersionRoleAndUnsupportedGroupAreRefused()
    {
        var (owner, serial) = await Setup();
        var (s, body) = await Submit(owner, serial, new { group = "operating_mode", based_on_version = "v-00000000", mode = "self_consumption" });
        Assert.Equal((HttpStatusCode.Conflict, "CONFIG_STALE"), (s, (string)body!["code"]!));
        Assert.NotNull(body["current_version"]);
        var v = await app.Version(owner, serial);
        (s, body) = await Submit(owner, serial, new { group = "operating_mode", based_on_version = v, mode = "max_grid_export" });
        Assert.Equal((HttpStatusCode.Forbidden, "ROLE_NOT_PERMITTED"), (s, (string)body!["code"]!));
        (s, body) = await Submit(owner, serial, new { group = "backup_soc", based_on_version = v, soc_percent = 3 });
        Assert.Equal((HttpStatusCode.BadRequest, "VALIDATION_FAILED"), (s, (string)body!["code"]!));
        (s, body) = await Submit(owner, serial, new { group = "backup_soc", based_on_version = v, soc_percent = 100 });
        Assert.Equal((HttpStatusCode.BadRequest, "VALIDATION_FAILED"), (s, (string)body!["code"]!));
        (s, _) = await Submit(owner, serial, new { group = "operating_mode", based_on_version = v, mode = "turbo" });
        Assert.Equal(HttpStatusCode.BadRequest, s);
        (s, _) = await Submit(owner, serial, new { group = "nonsense", based_on_version = v });
        Assert.Equal(HttpStatusCode.BadRequest, s);
        Assert.Empty(app.Devices.Received.Where(r => r.Serial == serial));
    }

    [Fact]
    public async Task IdempotencyKeyReturnsTheSameCommandAndRejectsADifferentBody()
    {
        var (owner, serial) = await Setup();
        var key = Guid.NewGuid().ToString();
        var body = new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "self_consumption" };
        var (s1, a) = await Submit(owner, serial, body, key);
        var (s2, b) = await Submit(owner, serial, body, key);
        Assert.Equal((HttpStatusCode.Accepted, HttpStatusCode.Accepted), (s1, s2));
        Assert.Equal((string)a!["command_id"]!, (string)b!["command_id"]!);
        var (s3, c) = await Submit(owner, serial, new { group = "operating_mode", based_on_version = body.based_on_version, mode = "tou" }, key);
        Assert.Equal((HttpStatusCode.UnprocessableEntity, "IDEMPOTENCY_KEY_REUSED"), (s3, (string)c!["code"]!));
        await Task.Delay(800);
        Assert.Single(app.Devices.Received.Where(r => r.Serial == serial));
    }

    [Fact]
    public async Task ValueRefusedByTheInverterFailsAndLeavesTheDeviceUnchanged()
    {
        var (owner, serial) = await Setup(FakeDevices.Behaviour.RefuseValue);
        var cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "self_consumption" });
        Assert.Equal("failed", (string)cmd["status"]!);
        Assert.Equal("MODBUS_EXCEPTION", (string)cmd["failure"]!["code"]!);
        Assert.Equal("old_unchanged", (string)cmd["outcome"]!["device_state"]!);
        Assert.Equal("battery_priority", (string)(await app.Get($"/api/v1/devices/{serial}/config", owner.Access)).Body!["mode"]!);
    }

    [Fact]
    public async Task AcknowledgedButNotAppliedIsRevertedNotReportedAsApplied()
    {
        var (owner, serial) = await Setup(FakeDevices.Behaviour.AckButIgnore);
        var cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "self_consumption" });
        Assert.Equal("reverted", (string)cmd["status"]!);
        Assert.Equal("READBACK_MISMATCH", (string)cmd["failure"]!["code"]!);
        Assert.Equal("old_restored", (string)cmd["outcome"]!["device_state"]!);
        Assert.False((bool)cmd["changes"]![0]!["readback_matched"]!);
    }

    [Fact]
    public async Task SilentDeviceTimesOutWithUnknownState()
    {
        var (owner, serial) = await Setup(FakeDevices.Behaviour.Silent);
        var cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "self_consumption" });
        Assert.Equal("failed", (string)cmd["status"]!);
        Assert.Equal("ACK_TIMEOUT", (string)cmd["failure"]!["code"]!);
        Assert.Equal("unknown", (string)cmd["outcome"]!["device_state"]!);
    }

    [Fact]
    public async Task TouScheduleIsStoredThenLoadedWhenTouIsSelected()
    {
        var (owner, serial) = await Setup();
        var (s, body) = await Submit(owner, serial, new { group = "tou_schedule", based_on_version = await app.Version(owner, serial), strategies = new[] { Strategy([Slot(22, 4), Slot(3, 5)]) } });
        Assert.Equal(HttpStatusCode.BadRequest, s);
        Assert.Contains(body!["errors"]!.AsArray(), e => (string)e!["code"]! == "SLOT_OVERLAP");

        // saved while not in TOU: nothing is written to the device
        var strategy = Strategy([Slot(22, 4), Slot(12, 14, 80, 1500)], [Slot(18, 21, 20, 5000)]);
        var cmd = await app.Command(owner, serial, new { group = "tou_schedule", based_on_version = await app.Version(owner, serial), strategies = new[] { strategy } });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.False((bool)cmd["outcome"]!["device_written"]!);
        Assert.Empty(cmd["outcome"]!["unsupported"]!.AsArray());
        Assert.Empty(app.Devices.Received.Where(r => r.Serial == serial));

        cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "tou" });
        Assert.Equal("applied", (string)cmd["status"]!);
        var config = (await app.Get($"/api/v1/devices/{serial}/config", owner.Access)).Body!;
        Assert.Equal("tou", (string)config["mode"]!);
        Assert.NotNull(config["today_strategy_id"]);
        var periods = app.Devices.State[serial]["charge_schedule"]!["periods"]!.AsArray();
        Assert.True((bool)app.Devices.State[serial]["charge_schedule"]!["enabled"]!);
        Assert.Equal(("22:00", "04:00", 3000, 90), ((string)periods[0]!["start_time"]!, (string)periods[0]!["end_time"]!, (int)periods[0]!["charge_power_w"]!, (int)periods[0]!["stop_soc"]!));
        Assert.Equal(("12:00", "14:00"), ((string)periods[1]!["start_time"]!, (string)periods[1]!["end_time"]!));
        Assert.Equal("00:00", (string)periods[2]!["start_time"]!);
        // the discharge slot is on the inverter as well, and both schedules are recorded as switched on
        var discharge = app.Devices.State[serial]["discharge_schedule"]!;
        Assert.True((bool)discharge["enabled"]!);
        var dp = discharge["periods"]!.AsArray();
        Assert.Equal(("18:00", "21:00", 5000, 20), ((string)dp[0]!["start_time"]!, (string)dp[0]!["end_time"]!, (int)dp[0]!["discharge_power_w"]!, (int)dp[0]!["stop_soc"]!));
        Assert.Equal(("00:00", "00:00"), ((string)dp[1]!["start_time"]!, (string)dp[1]!["end_time"]!));
        var names = cmd["changes"]!.AsArray().Select(c => (string)c!["parameter"]!).ToList();
        Assert.Contains("charge_schedule_enabled", names);
        Assert.Contains("discharge_schedule_enabled", names);
        Assert.All(cmd["changes"]!.AsArray(), c => Assert.True((bool)c!["readback_matched"]!));

        // editing the schedule while in TOU rewrites the discharge slots too
        var edited = Strategy([Slot(22, 4)], [Slot(17, 20, 30, 3500), Slot(6, 7, 25, 2000)]);
        cmd = await app.Command(owner, serial, new { group = "tou_schedule", based_on_version = await app.Version(owner, serial), strategies = new[] { edited } });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.True((bool)cmd["outcome"]!["device_written"]!);
        dp = app.Devices.State[serial]["discharge_schedule"]!["periods"]!.AsArray();
        Assert.Equal(("17:00", "20:00", 3500, 30), ((string)dp[0]!["start_time"]!, (string)dp[0]!["end_time"]!, (int)dp[0]!["discharge_power_w"]!, (int)dp[0]!["stop_soc"]!));
        Assert.Equal(("06:00", "07:00"), ((string)dp[1]!["start_time"]!, (string)dp[1]!["end_time"]!));

        // leaving TOU switches both schedules off again
        cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "battery_priority" });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.False((bool)app.Devices.State[serial]["charge_schedule"]!["enabled"]!);
        Assert.False((bool)app.Devices.State[serial]["discharge_schedule"]!["enabled"]!);
        Assert.Equal("charging", (string)app.Devices.State[serial]["pv_priority"]!);
    }

    [Fact]
    public async Task DischargeSlotsOnADataloggerWithoutSupportAreReportedNotSent()
    {
        // firmware before 1.0.17: no discharge_schedule in the settings report
        var (owner, serial) = await Setup();
        app.Devices.State[serial].Remove("discharge_schedule");
        await app.Feed(serial, "config/state", app.Devices.State[serial].ToJsonString());

        var strategy = Strategy([Slot(22, 4)], [Slot(18, 21, 20, 5000)]);
        var cmd = await app.Command(owner, serial, new { group = "tou_schedule", based_on_version = await app.Version(owner, serial), strategies = new[] { strategy } });
        Assert.Contains("discharge_slots", cmd["outcome"]!["unsupported"]!.AsArray().Select(x => (string)x!));
        cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "tou" });
        Assert.Equal("applied", (string)cmd["status"]!);
        Assert.Contains("discharge_slots", cmd["outcome"]!["unsupported"]!.AsArray().Select(x => (string)x!));
        Assert.DoesNotContain(app.Devices.Received, r => r.Serial == serial && r.Action == "SET_DISCHARGE_SCHEDULE");
        Assert.True((bool)app.Devices.State[serial]["charge_schedule"]!["enabled"]!);
    }

    [Fact]
    public async Task FailedChangeRestoresEveryPeriodExactly()
    {
        // settings made at the inverter's own screen: an unused period in the middle, per-period grid charging
        var (owner, serial) = await Setup(FakeDevices.Behaviour.AckButIgnore);
        var state = app.Devices.State[serial];
        var cp = state["charge_schedule"]!["periods"]!.AsArray();
        (cp[0]!["start_time"], cp[0]!["end_time"], cp[0]!["grid_charge"]) = ("01:00", "03:00", true);
        cp[1]!["grid_charge"] = false;
        (cp[2]!["start_time"], cp[2]!["end_time"], cp[2]!["grid_charge"]) = ("13:00", "15:00", true);
        await app.Feed(serial, "config/state", state.ToJsonString());

        var cmd = await app.Command(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "self_consumption" });
        Assert.Equal("reverted", (string)cmd["status"]!);
        // the restore addressed every period by number with its own values, including the unused one
        var restore = app.Devices.Received.Last(r => r.Serial == serial && r.Action == "SET_CHARGE_SCHEDULE").Params;
        var sent = restore["periods"]!.AsArray();
        Assert.Equal(3, sent.Count);
        Assert.Equal((1, "01:00", "03:00", true), ((int)sent[0]!["period"]!, (string)sent[0]!["start_time"]!, (string)sent[0]!["end_time"]!, (bool)sent[0]!["grid_charging"]!));
        Assert.Equal((2, "00:00", "00:00", false), ((int)sent[1]!["period"]!, (string)sent[1]!["start_time"]!, (string)sent[1]!["end_time"]!, (bool)sent[1]!["grid_charging"]!));
        Assert.Equal((3, "13:00", "15:00"), ((int)sent[2]!["period"]!, (string)sent[2]!["start_time"]!, (string)sent[2]!["end_time"]!));
        Assert.False((bool)restore["enabled"]!);
        Assert.Contains(app.Devices.Received, r => r.Serial == serial && r.Action == "SET_DISCHARGE_SCHEDULE");
    }

    [Fact]
    public async Task EmergencySwitchBlocksWritesAndOnlyStaffMaySelectGridExport()
    {
        var (owner, serial) = await Setup();
        var admin = await app.NewUser(role: "ggp_technician", writeAdmin: true);
        try
        {
            Assert.Equal(HttpStatusCode.OK, (await app.Send(HttpMethod.Put, "/api/v1/admin/config-write-switch", new { enabled = false, reason = "test" }, admin.Access)).Status);
            Assert.Equal("disabled_by_server", (string)(await app.Get($"/api/v1/devices/{serial}/config", owner.Access)).Body!["write_access"]!["state"]!);
            var (s, body) = await Submit(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "self_consumption" });
            Assert.Equal((HttpStatusCode.Conflict, "WRITE_DISABLED_BY_SERVER"), (s, (string)body!["code"]!));
        }
        finally
        {
            await app.Send(HttpMethod.Put, "/api/v1/admin/config-write-switch", new { enabled = true }, admin.Access);
        }
        Assert.DoesNotContain("max_grid_export", (await app.Get($"/api/v1/devices/{serial}/config", owner.Access)).Body!["write_access"]!["writable_modes"]!.AsArray().Select(x => (string)x!));
    }

    [Fact]
    public async Task OfflineDeviceRefusesWrites()
    {
        var (owner, serial) = await Setup();
        await app.Feed(serial, "status", new { sn = serial, state = "offline", reason = "connection_lost" });
        var (s, body) = await Submit(owner, serial, new { group = "operating_mode", based_on_version = await app.Version(owner, serial), mode = "self_consumption" });
        Assert.Equal((HttpStatusCode.Conflict, "GATEWAY_OFFLINE"), (s, (string)body!["code"]!));
    }
}

[Collection("app")]
public class ServiceTests(AppFixture app)
{
    [Fact]
    public async Task TicketConversationBetweenUserAndStaff()
    {
        var user = await app.NewUser();
        var other = await app.NewUser();
        var staff = await app.NewUser(role: "ggp_technician");
        var (s, ticket, _) = await app.Post("/api/v1/tickets", new { type = "technical", title = "Thử", description = "Mô tả" }, user.Access);
        Assert.Equal(HttpStatusCode.Created, s);
        var id = (string)ticket!["id"]!;
        Assert.StartsWith("#HT-", (string)ticket["code"]!);
        Assert.Equal(HttpStatusCode.NotFound, (await app.Get($"/api/v1/tickets/{id}", other.Access)).Status);
        Assert.Equal(HttpStatusCode.BadRequest, (await app.Post("/api/v1/tickets", new { type = "technical", title = "", description = "x" }, user.Access)).Status);

        Assert.Equal(HttpStatusCode.Created, (await app.Post($"/api/v1/admin/tickets/{id}/messages", new { text = "Đã nhận" }, staff.Access)).Status);
        var detail = (await app.Get($"/api/v1/tickets/{id}", user.Access)).Body!;
        Assert.Equal("processing", (string)detail["summary"]!["status"]!);
        Assert.Equal("support", (string)detail["messages"]!.AsArray().Last()!["author"]!);

        await app.Send(HttpMethod.Put, $"/api/v1/admin/tickets/{id}/status", new { status = "resolved" }, staff.Access);
        await app.Post($"/api/v1/tickets/{id}/messages", new { text = "Vẫn còn lỗi" }, user.Access);
        Assert.Equal("processing", (string)(await app.Get($"/api/v1/tickets/{id}", user.Access)).Body!["summary"]!["status"]!);
        await app.Send(HttpMethod.Put, $"/api/v1/admin/tickets/{id}/status", new { status = "closed" }, staff.Access);
        var (s2, body, _) = await app.Post($"/api/v1/tickets/{id}/messages", new { text = "x" }, user.Access);
        Assert.Equal((HttpStatusCode.Conflict, "TICKET_CLOSED"), (s2, (string)body!["code"]!));
    }

    [Fact]
    public async Task AttachmentsAreAcceptedByContentNotByClaim()
    {
        var user = await app.NewUser();
        var id = (string)(await app.Post("/api/v1/tickets", new { type = "other", title = "T", description = "D" }, user.Access)).Body!["id"]!;
        var png = new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }.Concat(new byte[64]).ToArray();
        var (s, body, _) = await app.Send(HttpMethod.Post, $"/api/v1/tickets/{id}/attachments", png, user.Access);
        Assert.Equal((HttpStatusCode.Created, "image/png"), (s, (string)body!["content_type"]!));
        (s, _, _) = await app.Send(HttpMethod.Post, $"/api/v1/tickets/{id}/attachments", Encoding.ASCII.GetBytes("MZ this is an executable"), user.Access);
        Assert.Equal(HttpStatusCode.UnsupportedMediaType, s);
    }

    [Fact]
    public async Task ProfileChangesAndWarrantyFromInstallDate()
    {
        var user = await app.NewUser();
        Assert.Equal(HttpStatusCode.NoContent, (await app.Send(HttpMethod.Patch, "/api/v1/me", new { name = "Tên Mới", language = "en", notifications = new { daily_report = false } }, user.Access)).Status);
        var me = (await app.Get("/api/v1/me", user.Access)).Body!;
        Assert.Equal(("Tên Mới", "en", false, true), ((string)me["name"]!, (string)me["language"]!, (bool)me["notifications"]!["daily_report"]!, (bool)me["notifications"]!["device_alerts"]!));
        Assert.Equal(HttpStatusCode.BadRequest, (await app.Send(HttpMethod.Patch, "/api/v1/me", new { language = "xx" }, user.Access)).Status);

        var serial = await app.NewDevice();
        var project = await app.NewProject(user, serial);
        var warranty = (await app.Get($"/api/v1/projects/{project}/warranty", user.Access)).Body!;
        var device = Assert.Single(warranty["devices"]!.AsArray())!;
        Assert.Equal(("2026-01-01", "2031-01-01"), ((string)device["activated_on"]!, (string)device["expires_on"]!));

        Assert.Equal(HttpStatusCode.Forbidden, (await app.Post("/api/v1/me/deletion", new { password = "wrong-pass1" }, user.Access)).Status);
        Assert.Equal(HttpStatusCode.Accepted, (await app.Post("/api/v1/me/deletion", new { password = user.Password }, user.Access)).Status);
        Assert.Equal(HttpStatusCode.Unauthorized, (await app.Post("/api/v1/auth/login", new { account = user.Email, password = user.Password })).Status);
    }
}

[Collection("app")]
public class RealtimeTests(AppFixture app)
{
    private static async Task<JsonNode> Receive(WebSocket ws, int seconds = 5)
    {
        var buffer = new byte[64 * 1024];
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(seconds));
        var res = await ws.ReceiveAsync(buffer, cts.Token);
        return JsonNode.Parse(Encoding.UTF8.GetString(buffer, 0, res.Count))!;
    }

    private async Task<WebSocket> Connect(AppFixture.User user)
    {
        var ticket = (string)(await app.Post("/api/v1/realtime/ticket", null, user.Access)).Body!["ticket"]!;
        return await app.Server.CreateWebSocketClient().ConnectAsync(new Uri(app.Server.BaseAddress, "/ws/v1/realtime?ticket=" + ticket), CancellationToken.None);
    }

    [Fact]
    public async Task SnapshotThenLiveUpdatesOnlyForOwnProjects()
    {
        var owner = await app.NewUser();
        var other = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);

        using (var stranger = await Connect(other))
        {
            await stranger.SendAsync(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new { subscribe = new[] { project } })), WebSocketMessageType.Text, true, CancellationToken.None);
            Assert.Empty((await Receive(stranger))["serials"]!.AsArray());
        }

        using var ws = await Connect(owner);
        await ws.SendAsync(Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new { subscribe = new[] { project } })), WebSocketMessageType.Text, true, CancellationToken.None);
        Assert.Equal(serial, (string)Assert.Single((await Receive(ws))["serials"]!.AsArray())!);
        var snapshot = await Receive(ws);
        Assert.Equal(("device", serial), ((string)snapshot["type"]!, (string)snapshot["serial"]!));

        await app.Feed(serial, "telemetry", AppFixture.Telemetry(serial, pv: 4321));
        var update = await Receive(ws);
        Assert.Equal(4321, (double)update["telemetry"]!["pv_w"]!);

        await app.Feed(serial, "status", new { sn = serial, state = "offline", reason = "connection_lost" });
        var offline = await Receive(ws);
        Assert.Equal("offline", (string)offline["status"]!);
        Assert.Null(offline["telemetry"]);
    }

    [Fact]
    public async Task TicketIsRequiredAndSingleUse()
    {
        var user = await app.NewUser();
        var ticket = (string)(await app.Post("/api/v1/realtime/ticket", null, user.Access)).Body!["ticket"]!;
        var client = app.Server.CreateWebSocketClient();
        using (await client.ConnectAsync(new Uri(app.Server.BaseAddress, "/ws/v1/realtime?ticket=" + ticket), CancellationToken.None)) { }
        await Assert.ThrowsAnyAsync<Exception>(() => client.ConnectAsync(new Uri(app.Server.BaseAddress, "/ws/v1/realtime?ticket=" + ticket), CancellationToken.None));
        await Assert.ThrowsAnyAsync<Exception>(() => client.ConnectAsync(new Uri(app.Server.BaseAddress, "/ws/v1/realtime?ticket=bogus"), CancellationToken.None));
        Assert.Equal(HttpStatusCode.Unauthorized, (await app.Post("/api/v1/realtime/ticket", null)).Status);
    }

    // An instance that stops (deploy, restart) lets go of its sockets at once. An open socket used to hold the web
    // server until the host's shutdown limit; the instance was then killed before it had written its ingest queue.
    [Fact]
    public async Task AnOpenSocketEndsWhenItsInstanceStops()
    {
        var user = await app.NewUser();
        await using var host = new ExtraHost([], 700000);
        var ticket = (string)(await host.Post("/api/v1/realtime/ticket", null, user.Access)).Body!["ticket"]!;
        using var ws = await host.Server.CreateWebSocketClient().ConnectAsync(new Uri(host.Server.BaseAddress, "/ws/v1/realtime?ticket=" + ticket), CancellationToken.None);

        host.Services.GetRequiredService<IHostApplicationLifetime>().StopApplication();

        // Still open after 5 s: ReceiveAsync is cancelled and the test fails.
        using var limit = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        try
        {
            var res = await ws.ReceiveAsync(new byte[1024], limit.Token);
            Assert.Equal(WebSocketMessageType.Close, res.MessageType);
        }
        catch (Exception e) when (e is WebSocketException or IOException) { /* dropped without a close frame: the app reconnects */ }
    }
}
