using System.Diagnostics.Metrics;

namespace GgPower.Infrastructure;

/// <summary>
/// Application metrics (plan A3), exported at <c>/metrics</c> in Prometheus format by the API host. Names follow
/// OpenTelemetry conventions; the exporter turns <c>ggpower.ingest.received</c> into <c>ggpower_ingest_received_total</c>.
/// The list with meanings is in backend/docs/METRICS.md.
/// </summary>
public static class GgMetrics
{
    public const string MeterName = "GgPower";
    public static readonly Meter Meter = new(MeterName);

    public static readonly Histogram<double> BatchDuration =
        Meter.CreateHistogram<double>("ggpower.writer.batch.duration", "ms", "Time to write one batch of raw samples");

    public static readonly Histogram<double> RollupDuration =
        Meter.CreateHistogram<double>("ggpower.rollup.duration", "ms", "Time of one statistics roll-up run");

    public static readonly Counter<long> CommandsSubmitted =
        Meter.CreateCounter<long>("ggpower.commands.submitted", description: "Config commands accepted, by group");

    public static readonly Counter<long> CommandsFinished =
        Meter.CreateCounter<long>("ggpower.commands.finished", description: "Config commands that reached a final status, by status");

    private static int _observed;

    /// <summary>Registers the gauges and counters read from live objects. Called once by the host; later calls are ignored.</summary>
    public static void Observe(IngestPipeline ingest, BatchWriter writer, RealtimeHub hub, LiveStateCache live, DeviceEventQueue events, JobLeader leader, TimeProvider clock)
    {
        if (Interlocked.Exchange(ref _observed, 1) == 1) return;
        Meter.CreateObservableGauge("ggpower.jobs.leader", () => JobLeader.Jobs.Select(j => new Measurement<int>(leader.Holds(j) ? 1 : 0, new KeyValuePair<string, object?>("job", j))),
            description: "1 when this instance runs the periodic job, by job");
        Meter.CreateObservableGauge("ggpower.events.queue.length", () => events.Pending, description: "Device events (status, alarms…) waiting for the database");
        Meter.CreateObservableCounter("ggpower.events.dropped", () => events.Dropped, description: "Status / firmware events dropped because the event queue was full");
        Meter.CreateObservableCounter("ggpower.events.failed", () => events.Failed, description: "Device events given up after retries");
        Meter.CreateObservableCounter("ggpower.ingest.received", () => ingest.Received, description: "MQTT messages received from devices");
        Meter.CreateObservableCounter("ggpower.ingest.rejected", () => ingest.Rejected, description: "Messages rejected (bad topic, tenant, serial or payload)");
        Meter.CreateObservableCounter("ggpower.ingest.dropped", () => ingest.Dropped, description: "Raw rows dropped because the write queue was full");
        Meter.CreateObservableGauge("ggpower.ingest.queue.length", () => ingest.QueueLength, description: "Raw rows waiting for the batch writer");
        Meter.CreateObservableGauge("ggpower.ingest.queue.capacity", () => ingest.QueueCapacity, description: "Write queue size before rows are dropped");
        Meter.CreateObservableCounter("ggpower.writer.rows", () => writer.Written, description: "Raw rows written to the database");
        Meter.CreateObservableGauge("ggpower.realtime.connections", () => hub.Connections, description: "Open WebSocket connections");
        Meter.CreateObservableGauge("ggpower.devices.known", () => live.All.Count(), description: "Devices in the live state");
        Meter.CreateObservableGauge("ggpower.devices.reachable", () =>
        {
            var now = clock.GetUtcNow();
            return live.All.Count(d => d.IsReachable(now));
        }, description: "Devices heard from within the last 90 s");
    }
}
