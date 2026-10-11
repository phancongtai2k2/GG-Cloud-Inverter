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
