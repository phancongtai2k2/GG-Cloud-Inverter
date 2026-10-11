using Dapper;
using GgPower.Infrastructure;

namespace GgPower.Api.Endpoints;

/// <summary>
/// Liveness / readiness for a load balancer or orchestrator (plan A2). <c>/healthz</c> keeps the detailed counters.
/// </summary>
public static class HealthEndpoints
{
    /// <summary>Above this share of the ingest queue the instance is too far behind to take more work.</summary>
    public const double QueueLimit = 0.8;

    public static void Map(WebApplication app)
    {
        // The process is up and serving requests; nothing else is checked, so a busy database never restarts it.
        app.MapGet("/healthz/live", () => Results.Json(new { status = "ok" })).DisableRateLimiting();

        // Can take traffic: database answers within 2 s, MQTT connected when this instance receives device data,
        // ingest queue not close to full. 503 lists what is wrong.
        app.MapGet("/healthz/ready", async (Db db, MqttLink mqtt, IngestPipeline ingest) =>
        {
            var problems = new List<string>();
            try
            {
                using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(2));
                await using var c = await db.OpenAsync(timeout.Token);
                await c.ExecuteScalarAsync<int>(new CommandDefinition("SELECT 1", cancellationToken: timeout.Token));
            }
            catch (Exception) { problems.Add("database"); }
            problems.AddRange(Problems(mqtt.Required, mqtt.IsConnected, ingest.QueueLength, ingest.QueueCapacity));
            return problems.Count == 0
                ? Results.Json(new { status = "ready" })
                : Results.Json(new { status = "not_ready", problems }, statusCode: StatusCodes.Status503ServiceUnavailable);
        }).DisableRateLimiting();
    }

    /// <summary>Readiness problems besides the database: MQTT down on an ingesting instance, ingest queue over <see cref="QueueLimit"/>.</summary>
    public static IEnumerable<string> Problems(bool mqttRequired, bool mqttConnected, int queueLength, int queueCapacity)
    {
        if (mqttRequired && !mqttConnected) yield return "mqtt";
        if (queueLength > queueCapacity * QueueLimit) yield return "ingest_queue";
    }
}
