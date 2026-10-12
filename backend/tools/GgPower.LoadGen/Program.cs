// Load generator: N simulated dataloggers publishing to an MQTT broker the way the real ones do
// (status once, telemetry every 5 s, battery every 20 s, random phase), and a periodic read of the
// backend's /healthz so the run prints what the backend kept up with.
//
//   dotnet run --project tools/GgPower.LoadGen -- --devices 2000 --minutes 10
//       [--broker localhost:1883] [--tenant ggp] [--prefix LOAD-] [--connections 4]
//       [--api http://localhost:5080] [--user u --password p]
//
// Used by step A0 of backend/docs/KE_HOACH_NANG_CAP_BACKEND.docx; results go to backend/docs/LOADTEST.md.
using System.Diagnostics;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using MQTTnet;
using MQTTnet.Client;
using MQTTnet.Protocol;

var opt = Options.Parse(args);
Console.WriteLine($"LoadGen: {opt.Devices} devices, {opt.Minutes} min, broker {opt.Host}:{opt.Port}, tenant {opt.Tenant}, {opt.Connections} connection(s)");

var factory = new MqttFactory();
var clients = new List<IMqttClient>();
for (var c = 0; c < opt.Connections; c++)
{
    var client = factory.CreateMqttClient();
    var b = new MqttClientOptionsBuilder().WithTcpServer(opt.Host, opt.Port)
        .WithClientId($"loadgen-{Environment.ProcessId}-{c}").WithCleanSession().WithKeepAlivePeriod(TimeSpan.FromSeconds(30));
    if (opt.User is not null) b = b.WithCredentials(opt.User, opt.Password);
    await client.ConnectAsync(b.Build());
    clients.Add(client);
}

var rnd = new Random(42);
var devices = Enumerable.Range(1, opt.Devices).Select(i => new Device($"{opt.Prefix}{i:D5}", rnd.NextDouble(), rnd.NextDouble())).ToArray();
long published = 0, failed = 0;

async Task Publish(int conn, string serial, string kind, object payload)
{
    var msg = new MqttApplicationMessageBuilder()
        .WithTopic($"solar/{opt.Tenant}/inverter/{serial}/up/{kind}")
        .WithPayload(JsonSerializer.SerializeToUtf8Bytes(payload))
        .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)
        .Build();
    try { await clients[conn].PublishAsync(msg); Interlocked.Increment(ref published); }
    catch { Interlocked.Increment(ref failed); }
}

// Status first, as a datalogger does when it connects.
await Parallel.ForEachAsync(devices.Select((d, i) => (d, i)), new ParallelOptions { MaxDegreeOfParallelism = 64 }, async (x, _) =>
    await Publish(x.i % opt.Connections, x.d.Serial, "status", new
    {
        sn = x.d.Serial, state = "online", model = "LoadGen", fw_ver = "V1", logger_fw = "1.0.17", inverter_comm = "ok",
        ip_addr = "10.0.0.1", signal_rssi = -60,
    }));

var stop = DateTime.UtcNow.AddMinutes(opt.Minutes);
var watch = Stopwatch.StartNew();
using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(5) };

// One scheduler per connection: every 100 ms, send what is due for its devices.
var loops = Enumerable.Range(0, opt.Connections).Select(conn => Task.Run(async () =>
{
    var mine = devices.Where((_, i) => i % opt.Connections == conn).ToArray();
    var start = DateTime.UtcNow;
    var nextTel = mine.Select(d => start.AddSeconds(d.Phase * 5)).ToArray();
    var nextBat = mine.Select(d => start.AddSeconds(d.Phase * 20)).ToArray();
    while (DateTime.UtcNow < stop)
    {
        var now = DateTime.UtcNow;
        // Real dataloggers publish independently: send what is due concurrently, not one QoS 1 round trip at a time.
        var due = new List<Task>();
        for (var k = 0; k < mine.Length; k++)
        {
            if (now >= nextTel[k]) { nextTel[k] = nextTel[k].AddSeconds(5); due.Add(Publish(conn, mine[k].Serial, "telemetry", mine[k].Telemetry(now))); }
            if (now >= nextBat[k]) { nextBat[k] = nextBat[k].AddSeconds(20); due.Add(Publish(conn, mine[k].Serial, "battery", mine[k].Battery(now))); }
        }
        await Task.WhenAll(due);
        await Task.Delay(100);
    }
})).ToArray();

// Progress every 10 s: our publish rate and what the backend reports.
var lastPub = 0L;
var lastTime = 0.0;
var samples = new List<string>();
while (DateTime.UtcNow < stop)
{
    await Task.Delay(TimeSpan.FromSeconds(10));
    var p = Interlocked.Read(ref published);
    var t = watch.Elapsed.TotalSeconds;
    var rate = (p - lastPub) / (t - lastTime);
    lastPub = p; lastTime = t;
    var health = "";
    if (opt.Api is not null)
    {
        try
        {
            var h = await http.GetFromJsonAsync<JsonElement>($"{opt.Api}/healthz");
            health = $" | backend received={h.GetProperty("messages_received")} written={h.GetProperty("rows_written")} dropped={h.GetProperty("rows_dropped")} last_batch_ms={h.GetProperty("last_batch_ms")}";
        }
        catch (Exception e) { health = $" | backend unreachable ({e.GetType().Name})"; }
    }
    var line = $"{t,6:F0}s published={p} ({rate:F0}/s) failed={Interlocked.Read(ref failed)}{health}";
    Console.WriteLine(line);
    samples.Add(line);
}
await Task.WhenAll(loops);
foreach (var c in clients) await c.DisconnectAsync();
Console.WriteLine($"Done: published {published} messages, {failed} failed, in {watch.Elapsed.TotalSeconds:F0} s.");

/// <summary>One simulated datalogger with a plausible daytime power curve.</summary>
sealed record Device(string Serial, double Phase, double Size)
{
    private double _yield;
    private DateTime _last = DateTime.UtcNow;

    private double Pv(DateTime now)
    {
        var hour = (now.AddHours(7) - now.AddHours(7).Date).TotalHours;   // Vietnam local time
        var sun = Math.Max(0, Math.Sin((hour - 6) / 12 * Math.PI));
        return Math.Round((3000 + Size * 5000) * sun);
    }

    public object Telemetry(DateTime now)
    {
        var pv = Pv(now);
        var load = 600 + Size * 1500;
        var batt = Math.Clamp(pv - load, -2500, 2500);   // surplus charges, deficit discharges
        var grid = load + batt - pv;                      // + import, − export
        _yield += pv * (now - _last).TotalHours / 1000;
        _last = now;
        return new
        {
            sn = Serial, ts = new DateTimeOffset(now).ToUnixTimeSeconds(), inv_status = 3, inv_temp = 38 + Size * 8,
            pv = new { v_pv1 = 320.0, i_pv1 = pv / 640, p_pv1 = pv / 2, v_pv2 = 318.0, i_pv2 = pv / 636, p_pv2 = pv / 2, p_total = pv },
            grid = new { v_ac = 230.0, freq = 50.0, p_active = Math.Round(grid) },
            load = new { v_out = 230.0, p_active = Math.Round(load) },
            battery = new { p_flow = -Math.Round(batt), soc = 60 },
            energy = new { yield_today_kwh = Math.Round(_yield, 2) },
        };
    }

    public object Battery(DateTime now) => new
    {
        sn = Serial, ts = new DateTimeOffset(now).ToUnixTimeSeconds(), soc = 60, soh = 98, voltage = 52.0, current = 3.0,
        present = true, bms_comm = "ok", capacity_ah_nominal = 300.0, capacity_ah_remaining = 180.0,
    };
}

sealed record Options(int Devices, double Minutes, string Host, int Port, string Tenant, string Prefix, int Connections, string? Api, string? User, string? Password)
{
    public static Options Parse(string[] a)
    {
        string? Get(string name) { var i = Array.IndexOf(a, "--" + name); return i >= 0 && i + 1 < a.Length ? a[i + 1] : null; }
        var devices = int.Parse(Get("devices") ?? "200");
        var broker = (Get("broker") ?? "localhost:1883").Split(':');
        return new Options(
            devices, double.Parse(Get("minutes") ?? "5", System.Globalization.CultureInfo.InvariantCulture),
            broker[0], broker.Length > 1 ? int.Parse(broker[1]) : 1883,
            Get("tenant") ?? "ggp", Get("prefix") ?? "LOAD-",
            int.Parse(Get("connections") ?? Math.Max(1, devices / 500).ToString()),
            Get("api"), Get("user"), Get("password"));
    }
}
