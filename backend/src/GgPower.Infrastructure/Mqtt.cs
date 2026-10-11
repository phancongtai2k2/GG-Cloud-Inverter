using System.Text;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using MQTTnet;
using MQTTnet.Client;
using MQTTnet.Protocol;

namespace GgPower.Infrastructure;

public sealed class MqttOptions
{
    public bool Enabled { get; set; } = true;
    public string Host { get; set; } = "";
    public int Port { get; set; } = 8883;
    public bool UseTls { get; set; } = true;
    public string Username { get; set; } = "";
    public string Password { get; set; } = "";
    /// <summary>
    /// Fixed client id. Needed for a persistent session on brokers that do not use the user name as client id
    /// (production Mosquitto does: <c>use_username_as_clientid</c>). Empty: <c>ggpower-backend-&lt;machine&gt;</c>.
    /// </summary>
    public string ClientId { get; set; } = "";

    /// <summary>
    /// Keep the session on the broker (clean session off): while the backend is down — a deploy, a crash — the broker
    /// queues the device messages and delivers them on reconnect, so no data is lost (plan A5). The broker's
    /// <c>max_queued_messages</c> bounds how long that can last.
    /// </summary>
    public bool PersistentSession { get; set; } = true;
}

public interface IMqttPublisher
{
    bool IsConnected { get; }
    Task PublishAsync(string topic, string payload, CancellationToken ct);
}

/// <summary>
/// The single MQTT connection of the backend: subscribes to every device's uplink for the configured
/// tenants and publishes commands. Reconnects with back-off; the broker's certificate is always verified.
/// </summary>
public sealed class MqttLink(IOptions<MqttOptions> options, IOptions<IngestOptions> ingest, IngestPipeline pipeline, ILogger<MqttLink> log)
    : BackgroundService, IMqttPublisher
{
    private readonly MqttOptions _o = options.Value;
    private IMqttClient? _client;

    public bool IsConnected => _client?.IsConnected == true;
    public DateTimeOffset? ConnectedSince { get; private set; }

    /// <summary>
    /// A persistent session is found again only under the same client id, so it must not change between runs;
    /// a throw-away session gets a unique one.
    /// </summary>
    public static string ClientIdFor(MqttOptions o) =>
        !string.IsNullOrEmpty(o.ClientId) ? o.ClientId
        : o.PersistentSession ? $"ggpower-backend-{Environment.MachineName}"
        : $"ggpower-backend-{Environment.MachineName}-{Guid.NewGuid():N}"[..40];

    /// <summary>This instance is meant to receive device data (MQTT enabled and a broker configured).</summary>
    public bool Required => _o.Enabled && !string.IsNullOrWhiteSpace(_o.Host);

    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        if (!Required)
        {
            log.LogWarning("MQTT is not configured (Mqtt:Host empty): no device data will be received");
            return;
        }

        var client = new MqttFactory().CreateMqttClient();
        _client = client;
        client.ApplicationMessageReceivedAsync += async e =>
        {
            try { await pipeline.HandleAsync(e.ApplicationMessage.Topic, e.ApplicationMessage.PayloadSegment, stop, e.ApplicationMessage.Retain); }
            catch (OperationCanceledException) { }
            catch (Exception ex) { log.LogError(ex, "failed to handle message on {Topic}", e.ApplicationMessage.Topic); }
        };

        var builder = new MqttClientOptionsBuilder()
            .WithTcpServer(_o.Host, _o.Port)
            .WithClientId(ClientIdFor(_o))
            .WithCleanSession(!_o.PersistentSession)
            .WithKeepAlivePeriod(TimeSpan.FromSeconds(30))
            .WithTimeout(TimeSpan.FromSeconds(15));
        if (!string.IsNullOrEmpty(_o.Username)) builder = builder.WithCredentials(_o.Username, _o.Password);
        if (_o.UseTls) builder = builder.WithTlsOptions(t => t.UseTls());
        var clientOptions = builder.Build();

        var delay = TimeSpan.FromSeconds(1);
        while (!stop.IsCancellationRequested)
        {
            try
            {
                if (!client.IsConnected)
                {
                    await client.ConnectAsync(clientOptions, stop);
                    var sub = new MqttClientSubscribeOptionsBuilder();
                    foreach (var tenant in ingest.Value.ActiveTenants)
                        sub = sub.WithTopicFilter(f => f.WithTopic($"solar/{tenant}/inverter/+/up/#").WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce));
                    await client.SubscribeAsync(sub.Build(), stop);
                    ConnectedSince = DateTimeOffset.UtcNow;
                    delay = TimeSpan.FromSeconds(1);
                    log.LogInformation("MQTT connected to {Host}:{Port}, tenants {Tenants}", _o.Host, _o.Port, string.Join(",", ingest.Value.ActiveTenants));
                }
                await Task.Delay(TimeSpan.FromSeconds(2), stop);
            }
            catch (OperationCanceledException) when (stop.IsCancellationRequested) { break; }
            catch (Exception ex)
            {
                ConnectedSince = null;
                log.LogWarning("MQTT connection failed: {Message}; retrying in {Delay}s", ex.Message, delay.TotalSeconds);
                await Task.Delay(delay, stop).ContinueWith(_ => { }, CancellationToken.None);
                // Kept short: dataloggers send the samples they stored 40 s after reconnecting, and this link must be back first.
                delay = TimeSpan.FromSeconds(Math.Min(delay.TotalSeconds * 2, 8));
            }
        }
        try { if (client.IsConnected) await client.DisconnectAsync(); } catch { /* shutting down */ }
        client.Dispose();
    }

    public async Task PublishAsync(string topic, string payload, CancellationToken ct)
    {
        var client = _client;
        if (client is null || !client.IsConnected) throw new InvalidOperationException("MQTT is not connected");
        var msg = new MqttApplicationMessageBuilder()
            .WithTopic(topic)
            .WithPayload(Encoding.UTF8.GetBytes(payload))
            .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)
            .Build();
        await client.PublishAsync(msg, ct);
    }
}
