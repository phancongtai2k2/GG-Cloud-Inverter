using System.Net;
using System.Text.Json;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using Dapper;
using GgPower.Infrastructure;
using Xunit;

namespace GgPower.Tests;

/// <summary>Checks for the scaling plan (backend/docs/KE_HOACH_NANG_CAP_BACKEND.docx).</summary>
[Collection("app")]
public class ScaleTests(AppFixture app)
{
    // A1: the case-insensitive serial lookups the API runs must be able to use an index (no full-table scan).
    [Theory]
    [InlineData("SELECT pd.project_id FROM project_devices pd WHERE upper(pd.serial) = upper(@serial)", "project_devices_serial_upper_ix")]
    [InlineData("SELECT 1 FROM config_allowlist WHERE upper(serial) = upper(@serial)", "config_allowlist_serial_upper_ix")]
    [InlineData("SELECT serial FROM devices WHERE upper(serial) = @serial", "devices_serial_upper_ix")]
    [InlineData("SELECT id FROM config_commands WHERE upper(serial) = upper(@serial) ORDER BY created_at DESC LIMIT 20", "config_commands_serial_upper_ix")]
    public async Task SerialLookupsUseAnIndex(string query, string index)
    {
        await using var c = await app.Service<Db>().OpenAsync();
        await using var tx = await c.BeginTransactionAsync();
        // The test tables are tiny, where a sequential scan is cheaper; forbid it to see whether an index fits the query.
        await c.ExecuteAsync("SET LOCAL enable_seqscan = off", transaction: tx);
        var plan = string.Join("\n", await c.QueryAsync<string>("EXPLAIN " + query.Replace("@serial", "'TEST-ABC'"), transaction: tx));
        Assert.Contains(index, plan);
    }

    // A4: the incremental roll-up gives the same numbers as a full pass, and leaves devices without new samples alone.
    [Fact]
    public async Task IncrementalRollupMatchesAFullPassAndSkipsQuietDevices()
    {
        var busy = await app.NewDevice();
        var quiet = await app.NewDevice();
        var rollup = app.Service<RollupService>();
        await using var c = await app.Service<Db>().OpenAsync();
        var now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        async Task FeedAndWait(string serial, long from)
        {
            for (var i = 0; i < 6; i++)
                await app.Feed(serial, "telemetry", AppFixture.Telemetry(serial, pv: 3000, grid: -500, load: 1500, battFlow: -1000, ts: from + i * 5));
            for (var k = 0; k < 50 && await c.ExecuteScalarAsync<int>("SELECT count(*) FROM telemetry_raw WHERE serial = @serial AND ts >= to_timestamp(@from)", new { serial, from }) < 6; k++)
                await Task.Delay(100);
            await Task.Delay(1200);   // the batch writer marks the rows for the roll-up right after writing them
        }
        const string Day = "SELECT pv_kwh, charge_kwh, pv_charge_kwh, soc_avg FROM rollup_1d WHERE serial = @serial ORDER BY day DESC LIMIT 1";

        await FeedAndWait(busy, now - 60);
        await FeedAndWait(quiet, now - 60);
        // The first call may be the start-up full pass; the second is incremental either way.
        await rollup.RunOnceAsync(DateTimeOffset.UtcNow, CancellationToken.None);
        await rollup.RunOnceAsync(DateTimeOffset.UtcNow, CancellationToken.None);

        // A quiet device keeps whatever its daily row holds: an incremental run does not touch it.
        await c.ExecuteAsync("UPDATE rollup_1d SET pv_kwh = 999 WHERE serial = @quiet", new { quiet });
        await FeedAndWait(busy, now - 25);
        await rollup.RunOnceAsync(DateTimeOffset.UtcNow, CancellationToken.None);
        Assert.Equal(999, await c.ExecuteScalarAsync<double>("SELECT pv_kwh FROM rollup_1d WHERE serial = @quiet ORDER BY day DESC LIMIT 1", new { quiet }));

        // The busy device's numbers from the incremental runs equal a full recomputation.
        var incremental = await c.QuerySingleAsync<(double Pv, double Chg, double PvChg, double Soc)>(Day, new { serial = busy });
        Assert.True(incremental.Pv > 0);
        await rollup.RunOnceAsync(DateTimeOffset.UtcNow, CancellationToken.None, lookBack: TimeSpan.FromMinutes(30));
        var full = await c.QuerySingleAsync<(double Pv, double Chg, double PvChg, double Soc)>(Day, new { serial = busy });
        Assert.Equal(full.Pv, incremental.Pv, 9);
        Assert.Equal(full.Chg, incremental.Chg, 9);
        Assert.Equal(full.PvChg, incremental.PvChg, 9);
        Assert.Equal(full.Soc, incremental.Soc, 3);
    }

    // A5: with a persistent session the broker may deliver a message again (QoS 1) or late, after newer ones.
    [Fact]
    public async Task RedeliveredOrLateMessagesChangeNothing()
    {
        var owner = await app.NewUser();
        var serial = await app.NewDevice();
        var project = await app.NewProject(owner, serial);
        var live = app.Service<LiveStateCache>().Get(serial)!;
        var ts = DateTimeOffset.UtcNow.ToUnixTimeSeconds() + 2;
        await app.Feed(serial, "telemetry", AppFixture.Telemetry(serial, pv: 3000, battFlow: -1500, ts: ts));
        var origin = (live.BattFromPvKwh, live.BattFromGridKwh);

        // The same sample again: one row, nothing integrated twice.
        await app.Feed(serial, "telemetry", AppFixture.Telemetry(serial, pv: 3000, battFlow: -1500, ts: ts));
        Assert.Equal(origin, (live.BattFromPvKwh, live.BattFromGridKwh));
        await using var c = await app.Service<Db>().OpenAsync();
        for (var k = 0; k < 30 && await c.ExecuteScalarAsync<int>("SELECT count(*) FROM telemetry_raw WHERE serial = @serial AND ts = to_timestamp(@ts)", new { serial, ts }) == 0; k++)
            await Task.Delay(100);
        await Task.Delay(1500);
        Assert.Equal(1, await c.ExecuteScalarAsync<int>("SELECT count(*) FROM telemetry_raw WHERE serial = @serial AND ts = to_timestamp(@ts)", new { serial, ts }));

        // An older sample arriving after a newer one is stored but does not take the live values back.
        await app.Feed(serial, "telemetry", AppFixture.Telemetry(serial, pv: 111, battFlow: -1500, ts: ts - 20));
        var t = (await app.Get($"/api/v1/projects/{project}/telemetry", owner.Access)).Body!["telemetry"]!;
        Assert.Equal(3000, (double)t["pv_w"]!);
        Assert.Equal(origin, (live.BattFromPvKwh, live.BattFromGridKwh));
    }

    // A5: the broker has its acknowledgement as soon as a sample is queued, so on a normal stop the writer must not drop
    // what is still in the queue.
    [Fact]
    public async Task ShutdownWritesQueuedSamples()
    {
        var sp = app.Services;
        var serial = await app.NewDevice();
        // A pipeline and writer of their own, so the running ones do not take these samples.
        var pipeline = new IngestPipeline(sp.GetRequiredService<Db>(), sp.GetRequiredService<LiveStateCache>(), sp.GetRequiredService<RealtimeHub>(),
            sp.GetRequiredService<IOptions<IngestOptions>>(), [], sp.GetRequiredService<BackfillTracker>(), sp.GetRequiredService<DeviceEventQueue>(),
            sp.GetRequiredService<TimeProvider>(),
            NullLogger<IngestPipeline>.Instance);
        var writer = new BatchWriter(sp.GetRequiredService<Db>(), pipeline, sp.GetRequiredService<LiveStateCache>(), sp.GetRequiredService<BackfillTracker>(),
            new RollupTracker(), NullLogger<BatchWriter>.Instance);
        var from = DateTimeOffset.UtcNow.ToUnixTimeSeconds() + 100;
        for (var i = 0; i < 20; i++)
            await pipeline.HandleAsync($"solar/ggp/inverter/{serial}/up/telemetry",
                JsonSerializer.SerializeToUtf8Bytes(AppFixture.Telemetry(serial, ts: from + i * 5)), CancellationToken.None);

        await writer.StartAsync(CancellationToken.None);
        await writer.StopAsync(CancellationToken.None);

        await using var c = await sp.GetRequiredService<Db>().OpenAsync();
        Assert.Equal(20, await c.ExecuteScalarAsync<int>("SELECT count(*) FROM telemetry_raw WHERE serial = @serial AND ts >= to_timestamp(@from)", new { serial, from }));
    }

    // A6: device events are written off the receive path — a stuck database does not hold up the MQTT handler.
    [Fact]
    public async Task StatusIsHandledWhileTheDatabaseWriteWaits()
    {
        var serial = await app.NewDevice();
        var queue = app.Service<DeviceEventQueue>();
        var release = new TaskCompletionSource();
        await queue.EnqueueAsync("blocker", _ => release.Task, mayDrop: false, CancellationToken.None);   // the "slow database"

        var watch = System.Diagnostics.Stopwatch.StartNew();
        await app.Service<IngestPipeline>().HandleAsync($"solar/ggp/inverter/{serial}/up/status",
            JsonSerializer.SerializeToUtf8Bytes(new { sn = serial, state = "online", model = "Queued Model" }), CancellationToken.None);
        Assert.True(watch.ElapsedMilliseconds < 1000, $"handler took {watch.ElapsedMilliseconds} ms");
        Assert.Equal("Queued Model", app.Service<LiveStateCache>().Get(serial)!.Model);   // memory is updated at once

        release.SetResult();
        await queue.DrainAsync();
        await using var c = await app.Service<Db>().OpenAsync();
        Assert.Equal("Queued Model", await c.ExecuteScalarAsync<string>("SELECT model FROM devices WHERE serial = @serial", new { serial }));
    }

    [Fact]
    public async Task FullEventQueueDropsStatusButWaitsForAlarms()
    {
        var queue = new DeviceEventQueue(NullLogger<DeviceEventQueue>.Instance);   // not started: nothing is consumed
        for (var i = 0; i < DeviceEventQueue.Capacity; i++)
            await queue.EnqueueAsync("status", _ => Task.CompletedTask, mayDrop: true, CancellationToken.None);
        await queue.EnqueueAsync("status", _ => Task.CompletedTask, mayDrop: true, CancellationToken.None);
        Assert.Equal(1, queue.Dropped);
        using var timeout = new CancellationTokenSource(TimeSpan.FromMilliseconds(200));
        await Assert.ThrowsAnyAsync<OperationCanceledException>(async () =>
            await queue.EnqueueAsync("alarm", _ => Task.CompletedTask, mayDrop: false, timeout.Token));
        Assert.Equal(DeviceEventQueue.Capacity, queue.Pending);
    }

    [Fact]
    public void PersistentSessionKeepsAStableClientId()
    {
        Assert.Equal(MqttLink.ClientIdFor(new MqttOptions()), MqttLink.ClientIdFor(new MqttOptions()));
        Assert.Equal("ggpower-backend-1", MqttLink.ClientIdFor(new MqttOptions { ClientId = "ggpower-backend-1" }));
        var throwAway = new MqttOptions { PersistentSession = false };
        Assert.NotEqual(MqttLink.ClientIdFor(throwAway), MqttLink.ClientIdFor(throwAway));
    }

    // B1: one leader per job. Two "instances" compete in the postgres database (advisory locks are per database, so
    // they do not meet the test host's own leader); the second takes over when the first stops or loses its session.
    [Fact]
    public async Task OnlyOneInstanceLeadsAJobAndAnotherTakesOver()
    {
        var cs = new Npgsql.NpgsqlConnectionStringBuilder(Environment.GetEnvironmentVariable("GGP_ConnectionStrings__Main")) { Database = "postgres" }.ToString();
        await using var db = new Db(cs);
        await using var a = new JobLeader(db, NullLogger<JobLeader>.Instance);
        await using var b = new JobLeader(db, NullLogger<JobLeader>.Instance);

        Assert.True(await a.TryLeadAsync(JobLeader.Rollup, CancellationToken.None));
        Assert.False(await b.TryLeadAsync(JobLeader.Rollup, CancellationToken.None));
        Assert.True(await b.TryLeadAsync(JobLeader.DailyReport, CancellationToken.None));   // each job has its own leader
        Assert.True(await a.TryLeadAsync(JobLeader.Rollup, CancellationToken.None));         // and keeps it while alive

        // A leader that stops hands over at the follower's next try.
        await a.ReleaseAsync(JobLeader.Rollup);
        Assert.True(await b.TryLeadAsync(JobLeader.Rollup, CancellationToken.None));
        Assert.False(await a.TryLeadAsync(JobLeader.Rollup, CancellationToken.None));

        // A leader whose connection drops (crash, network) loses the job: Postgres frees the lock with the session.
        await using (var admin = await db.OpenAsync())
            Assert.True(await admin.ExecuteScalarAsync<bool>(
                """
                SELECT pg_terminate_backend(pid) FROM pg_locks
                WHERE locktype = 'advisory' AND classid = 18247 AND objid = 1 AND database = (SELECT oid FROM pg_database WHERE datname = 'postgres')
                """));
        await Task.Delay(200);
        Assert.True(await a.TryLeadAsync(JobLeader.Rollup, CancellationToken.None));
        Assert.False(await b.TryLeadAsync(JobLeader.Rollup, CancellationToken.None));
        Assert.False(b.Holds(JobLeader.Rollup));
    }

    [Fact]
    public async Task TheTestHostLeadsItsJobs()
    {
        var leader = app.Service<JobLeader>();
        for (var i = 0; i < 50 && !JobLeader.Jobs.All(leader.Holds); i++) await Task.Delay(100);
        Assert.All(JobLeader.Jobs, j => Assert.True(leader.Holds(j), j));
        var metrics = await (await app.Get("/metrics", null)).Raw.Content.ReadAsStringAsync();
        Assert.Matches("""ggpower_jobs_leader\{[^}]*job="rollup"[^}]*\} 1\b""", metrics);
    }

    // B1: commands are closed by age, not at start-up, so a starting instance leaves the others' running commands alone.
    [Fact]
    public async Task OnlyAbandonedCommandsAreClosed()
    {
        var serial = await app.NewDevice();
        Guid stale = Guid.NewGuid(), running = Guid.NewGuid();
        await using var c = await app.Service<Db>().OpenAsync();
        const string Insert =
            """
            INSERT INTO config_commands (id, serial, grp, status, request, request_hash, idempotency_key, created_at, updated_at, expires_at)
            VALUES (@id, @serial, 'operating_mode', 'writing', '{}', '\x00', @id, @at, @at, @at)
            """;
        await c.ExecuteAsync(Insert, new { id = stale, serial, at = DateTimeOffset.UtcNow - CommandService.AbandonedAfter - TimeSpan.FromMinutes(1) });
        await c.ExecuteAsync(Insert, new { id = running, serial, at = DateTimeOffset.UtcNow });

        await app.Service<CommandService>().CloseAbandonedAsync(CancellationToken.None);

        const string Status = "SELECT status || ':' || COALESCE(failure->>'code', '') FROM config_commands WHERE id = @id";
        Assert.Equal("failed:ACK_TIMEOUT", await c.ExecuteScalarAsync<string>(Status, new { id = stale }));
        Assert.Equal("writing:", await c.ExecuteScalarAsync<string>(Status, new { id = running }));
    }

    // A2: liveness never depends on anything else; readiness checks the database, MQTT (when ingesting) and the queue.
    [Fact]
    public async Task HealthEndpointsReportLiveAndReady()
    {
        var live = await app.Get("/healthz/live", null);
        Assert.Equal((HttpStatusCode.OK, "ok"), (live.Status, (string)live.Body!["status"]!));
        // The test host has MQTT disabled, so only the database and the queue count.
        var ready = await app.Get("/healthz/ready", null);
        Assert.Equal((HttpStatusCode.OK, "ready"), (ready.Status, (string)ready.Body!["status"]!));
    }

    // A3: application metrics in Prometheus format, including the ingest counters and the HTTP metrics.
    [Fact]
    public async Task MetricsAreExportedForPrometheus()
    {
        await app.NewDevice();   // at least one message received and one device known
        var res = await app.Get("/metrics", null);
        Assert.Equal(HttpStatusCode.OK, res.Status);
        var text = await res.Raw.Content.ReadAsStringAsync();
        foreach (var name in new[] { "ggpower_ingest_received_total", "ggpower_ingest_queue_length", "ggpower_writer_rows_total", "ggpower_devices_known", "http_server_request_duration_seconds" })
            Assert.Contains(name, text);
    }

    [Theory]
    [InlineData(false, false, 0, 100, "")]
    [InlineData(true, true, 80, 100, "")]
    [InlineData(true, false, 0, 100, "mqtt")]
    [InlineData(false, false, 81, 100, "ingest_queue")]
    [InlineData(true, false, 100, 100, "mqtt,ingest_queue")]
    public void ReadinessProblems(bool mqttRequired, bool mqttConnected, int queue, int capacity, string expected)
    {
        Assert.Equal(expected, string.Join(",", GgPower.Api.Endpoints.HealthEndpoints.Problems(mqttRequired, mqttConnected, queue, capacity)));
    }
}
