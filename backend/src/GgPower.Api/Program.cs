using System.IO.Compression;
using System.Text.Json;
using System.Threading.RateLimiting;
using GgPower.Api;
using GgPower.Api.Endpoints;
using GgPower.Infrastructure;
using OpenTelemetry.Metrics;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.AspNetCore.ResponseCompression;

var builder = WebApplication.CreateBuilder(args);
var cfg = builder.Configuration;
// Secrets (database password, JWT key, MQTT account) come from user-secrets, environment variables
// or an untracked appsettings.Local.json - never from the repository.
cfg.AddJsonFile("appsettings.Local.json", optional: true, reloadOnChange: false);
cfg.AddEnvironmentVariables("GGP_");

builder.WebHost.ConfigureKestrel(k =>
{
    k.AddServerHeader = false;
    k.Limits.MaxRequestBodySize = 1 * 1024 * 1024;       // raised per endpoint for uploads
    k.Limits.RequestHeadersTimeout = TimeSpan.FromSeconds(15);
});

builder.Services.Configure<SecurityOptions>(cfg.GetSection("Security"));
builder.Services.Configure<MqttOptions>(cfg.GetSection("Mqtt"));
builder.Services.Configure<IngestOptions>(cfg.GetSection("Ingest"));
builder.Services.Configure<StatsOptions>(cfg.GetSection("Stats"));
builder.Services.Configure<CommandOptions>(cfg.GetSection("Commands"));
builder.Services.Configure<ContentOptions>(cfg.GetSection("Content"));

var connectionString = cfg.GetConnectionString("Main")
    ?? throw new InvalidOperationException("ConnectionStrings:Main is not configured");
builder.Services.AddSingleton(new Db(connectionString));
builder.Services.AddSingleton(TimeProvider.System);
builder.Services.AddSingleton<Migrator>();
builder.Services.AddSingleton<PartitionMaintenance>();
builder.Services.AddSingleton<TokenService>();
builder.Services.AddSingleton<IOtpSender, LoggingOtpSender>();
builder.Services.AddSingleton<IPushSender, LoggingPushSender>();
builder.Services.AddSingleton<AuthService>();
builder.Services.AddSingleton<LiveStateCache>();
builder.Services.AddSingleton<RealtimeHub>();
// State the API instances share (plan B3). "memory" is right for a single instance; "redis" comes with B4.
var sharedProvider = builder.Configuration["Shared:Provider"] ?? "memory";
if (sharedProvider != "memory") throw new InvalidOperationException($"Shared:Provider '{sharedProvider}' is not supported (memory)");
builder.Services.AddSingleton<ITicketStore, MemoryTicketStore>();
builder.Services.AddSingleton<ICommandCoordinator, MemoryCommandCoordinator>();
builder.Services.AddSingleton<ICacheVersions, MemoryCacheVersions>();
builder.Services.AddSingleton<IRealtimeBus, LocalRealtimeBus>();
builder.Services.AddSingleton<BackfillTracker>();
builder.Services.AddSingleton<RollupTracker>();
// One instance per periodic job (plan B1); disposed after the hosted services stop, which frees its locks.
builder.Services.AddSingleton<JobLeader>();
// Registered before MqttLink: hosted services stop in reverse order, so it still writes what MQTT delivered last.
builder.Services.AddSingleton<DeviceEventQueue>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<DeviceEventQueue>());
builder.Services.AddSingleton<IngestPipeline>();
builder.Services.AddSingleton<BatchWriter>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<BatchWriter>());
builder.Services.AddSingleton<RollupService>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<RollupService>());
builder.Services.AddSingleton<MqttLink>();
builder.Services.AddSingleton<IMqttPublisher>(sp => sp.GetRequiredService<MqttLink>());
builder.Services.AddHostedService(sp => sp.GetRequiredService<MqttLink>());
builder.Services.AddSingleton<CommandService>();
builder.Services.AddSingleton<IDeviceEventSink>(sp => sp.GetRequiredService<CommandService>());
builder.Services.AddHostedService(sp => sp.GetRequiredService<CommandService>());
builder.Services.AddSingleton<NotificationService>();
builder.Services.AddSingleton<IDeviceEventSink>(sp => sp.GetRequiredService<NotificationService>());
builder.Services.AddHostedService(sp => sp.GetRequiredService<NotificationService>());
builder.Services.AddHostedService<LiveMonitor>();
builder.Services.AddSingleton<AccessService>();
builder.Services.AddSingleton<AuditLog>();
builder.Services.AddSingleton<IFileStore, LocalFileStore>();
builder.Services.AddSingleton<StatsService>();
builder.Services.AddMemoryCache(o => o.SizeLimit = null);
// Metrics for Prometheus at /metrics (plan A3): application meter + ASP.NET Core HTTP metrics. Not routed by Caddy.
builder.Services.AddOpenTelemetry().WithMetrics(m => m
    .AddMeter(GgMetrics.MeterName)
    .AddAspNetCoreInstrumentation()
    .AddPrometheusExporter());

builder.Services.ConfigureHttpJsonOptions(o =>
{
    o.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
    o.SerializerOptions.DictionaryKeyPolicy = null;
    o.SerializerOptions.PropertyNameCaseInsensitive = false;
});

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme).AddJwtBearer();
builder.Services.AddOptions<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme)
    .Configure<TokenService>((o, tokens) =>
    {
        o.MapInboundClaims = false;
        o.TokenValidationParameters = tokens.ValidationParameters;
        o.Events = new JwtBearerEvents
        {
            // An access token stops working shortly after its session is signed out or revoked,
            // not only when it expires. The answer is cached briefly to keep requests off the database.
            OnTokenValidated = async ctx =>
            {
                var cache = ctx.HttpContext.RequestServices.GetRequiredService<Microsoft.Extensions.Caching.Memory.IMemoryCache>();
                if (!Guid.TryParse(ctx.Principal?.FindFirst("sid")?.Value, out var sid)) { ctx.Fail("no session"); return; }
                var key = "sid:" + sid.ToString("N");
                if (!Microsoft.Extensions.Caching.Memory.CacheExtensions.TryGetValue(cache, key, out AuthService.SessionState? state))
                {
                    state = await ctx.HttpContext.RequestServices.GetRequiredService<AuthService>().SessionStateAsync(sid, ctx.HttpContext.RequestAborted);
                    Microsoft.Extensions.Caching.Memory.CacheExtensions.Set(cache, key, state, TimeSpan.FromSeconds(20));
                }
                if (state is null) { ctx.Fail("session revoked"); return; }
                // users.role is the only source of a user's role: what the token says is replaced by the
                // current value, so a change applies within seconds and without signing in again.
                var identity = (System.Security.Claims.ClaimsIdentity)ctx.Principal!.Identity!;
                foreach (var old in identity.FindAll(cl => cl.Type is "role" or "write_admin").ToList()) identity.RemoveClaim(old);
                identity.AddClaim(new System.Security.Claims.Claim("role", state.Role));
                if (state.WriteAdmin) identity.AddClaim(new System.Security.Claims.Claim("write_admin", "1"));
            },
            OnChallenge = async ctx =>
            {
                ctx.HandleResponse();
                ctx.Response.StatusCode = 401;
                await ctx.Response.WriteAsJsonAsync(new { code = "UNAUTHENTICATED" });
            },
            OnForbidden = async ctx =>
            {
                ctx.Response.StatusCode = 403;
                await ctx.Response.WriteAsJsonAsync(new { code = "ROLE_NOT_PERMITTED" });
            },
        };
    });
builder.Services.AddAuthorizationBuilder()
    .AddPolicy("write_admin", p => p.RequireAuthenticatedUser().RequireClaim("write_admin", "1"))
    .AddPolicy("staff", p => p.RequireAuthenticatedUser().RequireClaim("role", GgPower.Domain.Roles.Technician));

// Rate limits: tight on the unauthenticated account endpoints (per client address), generous per user elsewhere.
builder.Services.AddRateLimiter(o =>
{
    o.RejectionStatusCode = 429;
    o.OnRejected = async (ctx, ct) =>
    {
        var retry = ctx.Lease.TryGetMetadata(MetadataName.RetryAfter, out var r) ? (int)Math.Ceiling(r.TotalSeconds) : 60;
        ctx.HttpContext.Response.Headers.RetryAfter = retry.ToString();
        await ctx.HttpContext.Response.WriteAsJsonAsync(new { code = "RATE_LIMITED", retry_after_seconds = retry }, ct);
    };
    var authPerMinute = cfg.GetValue("RateLimits:AuthPerMinute", 20);
    var apiPerMinute = cfg.GetValue("RateLimits:ApiPerMinute", 600);
    o.AddPolicy("auth", ctx => RateLimitPartition.GetFixedWindowLimiter(
        ctx.Connection.RemoteIpAddress?.ToString() ?? "unknown",
        _ => new FixedWindowRateLimiterOptions { PermitLimit = authPerMinute, Window = TimeSpan.FromMinutes(1), QueueLimit = 0 }));
    o.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(ctx =>
        RateLimitPartition.GetTokenBucketLimiter(
            ctx.User.Identity?.IsAuthenticated == true ? "u:" + ctx.User.FindFirst("sub")?.Value : "ip:" + ctx.Connection.RemoteIpAddress,
            _ => new TokenBucketRateLimiterOptions
            {
                TokenLimit = apiPerMinute, TokensPerPeriod = apiPerMinute, ReplenishmentPeriod = TimeSpan.FromMinutes(1), QueueLimit = 0, AutoReplenishment = true,
            }));
});

builder.Services.AddResponseCompression(o =>
{
    o.EnableForHttps = true;      // responses carry no secrets mixed with attacker-controlled input
    o.Providers.Add<BrotliCompressionProvider>();
    o.Providers.Add<GzipCompressionProvider>();
});
builder.Services.Configure<BrotliCompressionProviderOptions>(o => o.Level = CompressionLevel.Fastest);

// Behind a reverse proxy (Caddy on the VPS) the client address comes from X-Forwarded-For of that proxy only.
builder.Services.Configure<ForwardedHeadersOptions>(o =>
{
    o.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
    foreach (var p in cfg.GetSection("Proxy:Known").Get<string[]>() ?? [])
        if (System.Net.IPAddress.TryParse(p, out var ip)) o.KnownProxies.Add(ip);
});

var app = builder.Build();
// The realtime frame computes "CO₂ avoided today" with the same factor as the statistics endpoints.
LiveJson.Co2KgPerKwh = app.Services.GetRequiredService<Microsoft.Extensions.Options.IOptions<StatsOptions>>().Value.Co2KgPerKwh;

await app.Services.GetRequiredService<Migrator>().MigrateAsync();
await app.Services.GetRequiredService<PartitionMaintenance>()
    .RunAsync(app.Services.GetRequiredService<Microsoft.Extensions.Options.IOptions<IngestOptions>>().Value.RawRetentionDays, DateTimeOffset.UtcNow);
await app.Services.GetRequiredService<IngestPipeline>().WarmUpAsync(CancellationToken.None);

app.UseForwardedHeaders();
app.Use(async (ctx, next) =>
{
    var h = ctx.Response.Headers;
    h.XContentTypeOptions = "nosniff";
    h["Referrer-Policy"] = "no-referrer";
    h.XFrameOptions = "DENY";
    h.ContentSecurityPolicy = "default-src 'none'; frame-ancestors 'none'";
    if (ctx.Request.IsHttps) h.StrictTransportSecurity = "max-age=31536000; includeSubDomains";
    await next();
});
// Unhandled errors: a generic body, details only in the server log.
app.UseExceptionHandler(a => a.Run(async ctx =>
{
    ctx.Response.StatusCode = 500;
    await ctx.Response.WriteAsJsonAsync(new { code = "SERVER_ERROR" });
}));
app.UseStatusCodePages(async sc =>
{
    var r = sc.HttpContext.Response;
    if (r.HasStarted || r.ContentLength > 0) return;
    var code = r.StatusCode switch { 400 => "VALIDATION_FAILED", 404 => "NOT_FOUND", 405 => "METHOD_NOT_ALLOWED", 413 => "PAYLOAD_TOO_LARGE", 415 => "UNSUPPORTED_MEDIA_TYPE", _ => null };
    if (code is not null) await r.WriteAsJsonAsync(new { code });
});
app.UseResponseCompression();
app.UseWebSockets(new WebSocketOptions { KeepAliveInterval = TimeSpan.FromSeconds(20) });
app.UseAuthentication();
app.UseRateLimiter();
app.UseAuthorization();

app.MapGet("/healthz", (MqttLink mqtt, RealtimeHub hub, BatchWriter writer, IngestPipeline ingest) => Results.Json(new
{
    status = "ok",
    mqtt_connected = mqtt.IsConnected,
    realtime_connections = hub.Connections,
    messages_received = ingest.Received,
    rows_written = writer.Written,
    rows_dropped = ingest.Dropped,
    last_batch_ms = Math.Round(writer.LastBatchMs, 1),
})).DisableRateLimiting();

HealthEndpoints.Map(app);
GgMetrics.Observe(app.Services.GetRequiredService<IngestPipeline>(), app.Services.GetRequiredService<BatchWriter>(),
    app.Services.GetRequiredService<RealtimeHub>(), app.Services.GetRequiredService<LiveStateCache>(), app.Services.GetRequiredService<DeviceEventQueue>(),
    app.Services.GetRequiredService<JobLeader>(), app.Services.GetRequiredService<TimeProvider>());
app.MapPrometheusScrapingEndpoint("/metrics").WithMetadata(new Microsoft.AspNetCore.Routing.HttpMethodMetadata(["GET"])).DisableRateLimiting();

var api = app.MapGroup("/api/v1");
AuthEndpoints.Map(api);
ProfileEndpoints.Map(api);
ProjectEndpoints.Map(api);
DataEndpoints.Map(api);
AlertEndpoints.Map(api);
ConfigEndpoints.Map(api);
ServiceEndpoints.Map(api);
RealtimeEndpoints.Map(app, api);

app.Run();

public partial class Program;
