using System.Net;
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
