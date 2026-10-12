using System.Security.Claims;
using System.Security.Cryptography;
using System.Text.Json;
using Dapper;
using GgPower.Infrastructure;
using Microsoft.Extensions.Caching.Memory;

namespace GgPower.Api;

/// <summary>Every error body is <c>{"code": "..."}</c> plus optional details; the app owns the wording.</summary>
public static class Api
{
    public static IResult Error(int status, string code, object? extra = null)
    {
        var body = new Dictionary<string, object?> { ["code"] = code };
        if (extra is not null)
            foreach (var p in extra.GetType().GetProperties()) body[JsonNamingPolicy.SnakeCaseLower.ConvertName(p.Name)] = p.GetValue(extra);
        return Results.Json(body, statusCode: status);
    }

    public static IResult NotFound(string code = "NOT_FOUND") => Error(404, code);
    public static IResult Invalid(object? errors = null) => Error(400, "VALIDATION_FAILED", errors is null ? null : new { errors });

    public static Guid UserId(this ClaimsPrincipal u) => Guid.Parse(u.FindFirstValue("sub")!);
    public static string Role(this ClaimsPrincipal u) => u.FindFirstValue("role") ?? "owner";
    public static bool WriteAdmin(this ClaimsPrincipal u) => u.HasClaim("write_admin", "1");
    public static Guid SessionFamily(this ClaimsPrincipal u) => Guid.Parse(u.FindFirstValue("sid")!);

    public static string? ClientIp(this HttpContext ctx) => ctx.Connection.RemoteIpAddress?.ToString();

    /// <summary>
    /// Conditional GET: returns 304 when the client's copy is current. <paramref name="immutable"/> marks
    /// data that can never change again (a closed day), so the client may keep it without asking.
    /// </summary>
    public static IResult Cached(HttpContext ctx, object body, bool immutable = false, int maxAgeSeconds = 0)
    {
        var bytes = JsonSerializer.SerializeToUtf8Bytes(body, LiveJson.Options);
        var etag = "\"" + Convert.ToHexString(SHA256.HashData(bytes), 0, 12) + "\"";
        ctx.Response.Headers.ETag = etag;
        ctx.Response.Headers.CacheControl = immutable ? "private, max-age=31536000, immutable"
            : maxAgeSeconds > 0 ? $"private, max-age={maxAgeSeconds}" : "private, no-cache";
        if (ctx.Request.Headers.IfNoneMatch.ToString() == etag) return Results.StatusCode(304);
        return Results.Bytes(bytes, "application/json; charset=utf-8");
    }
}

public sealed class ProjectRow
{
    public Guid Id { get; set; }
    public Guid OwnerId { get; set; }
    public string Name { get; set; } = "";
    public string SiteType { get; set; } = "home";
    public string Address { get; set; } = "";
    public double? Latitude { get; set; }
    public double? Longitude { get; set; }
    public string TimeZone { get; set; } = "Asia/Ho_Chi_Minh";
    public DateTime InstalledOn { get; set; }
    public double PvKwp { get; set; }
    public string? PhotoFile { get; set; }
    public string TariffKind { get; set; } = "evn_tiered";
    public long? TariffFixed { get; set; }
    public long TariffSell { get; set; }
    public string Role { get; set; } = "";
}

public sealed record ProjectAccess(ProjectRow Project, string Role, string[] Serials)
{
    /// <summary>The device whose live values represent the project (the first one added).</summary>
    public string? PrimarySerial => Serials.FirstOrDefault();

    public TimeZoneInfo Zone
    {
        get
        {
            try { return TimeZoneInfo.FindSystemTimeZoneById(Project.TimeZone); }
            catch (TimeZoneNotFoundException) { return TimeZoneInfo.FindSystemTimeZoneById("Asia/Ho_Chi_Minh"); }
        }
    }
}

/// <summary>
/// The only way endpoints obtain a project or device: membership is checked here, so a caller can
/// never read or change something outside their own projects. A miss is reported as "not found".
/// Membership says which projects a user sees; what the user may do there comes from users.role alone.
/// </summary>
public sealed class AccessService(Db db, IMemoryCache cache)
{
    private const string ProjectColumns =
        "p.id, p.owner_id, p.name, p.site_type, p.address, p.latitude, p.longitude, p.time_zone, p.installed_on, p.pv_kwp, p.photo_file, p.tariff_kind, p.tariff_fixed, p.tariff_sell";

    public async Task<ProjectAccess?> ProjectAsync(ClaimsPrincipal user, Guid projectId, CancellationToken ct)
    {
        var key = $"access:{user.UserId():N}:{projectId:N}";
        if (cache.TryGetValue(key, out ProjectAccess? hit)) return hit;
        await using var c = await db.OpenAsync(ct);
        var found = await c.QuerySingleOrDefaultAsync<ProjectRow>(
            $"SELECT {ProjectColumns}, u.role FROM projects p JOIN project_members m ON m.project_id = p.id JOIN users u ON u.id = m.user_id WHERE p.id = @projectId AND m.user_id = @userId",
            new { projectId, userId = user.UserId() });
        if (found is null) return null;
        var serials = (await c.QueryAsync<string>("SELECT serial FROM project_devices WHERE project_id = @projectId ORDER BY added_at", new { projectId })).ToArray();
        var access = new ProjectAccess(found, found.Role, serials);
        cache.Set(key, access, TimeSpan.FromSeconds(30));
        return access;
    }

    /// <summary>Access by device serial (configuration endpoints). Returns null unless the caller's project holds the device.</summary>
    public async Task<ProjectAccess?> DeviceAsync(ClaimsPrincipal user, string serial, CancellationToken ct)
    {
        if (!GgPower.Domain.UpTopic.IsSerial(serial)) return null;
        await using var c = await db.OpenAsync(ct);
        var projectId = await c.ExecuteScalarAsync<Guid?>(
            "SELECT pd.project_id FROM project_devices pd JOIN project_members m ON m.project_id = pd.project_id WHERE upper(pd.serial) = upper(@serial) AND m.user_id = @userId",
            new { serial, userId = user.UserId() });
        return projectId is null ? null : await ProjectAsync(user, projectId.Value, ct);
    }

    public void Invalidate(Guid userId, Guid projectId) => cache.Remove($"access:{userId:N}:{projectId:N}");

    public async Task<List<(ProjectRow Project, string[] Serials)>> ProjectsOfAsync(Guid userId, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        var projects = (await c.QueryAsync<ProjectRow>(
            $"SELECT {ProjectColumns}, u.role FROM projects p JOIN project_members m ON m.project_id = p.id JOIN users u ON u.id = m.user_id WHERE m.user_id = @userId ORDER BY p.created_at", new { userId })).ToList();
        var devices = (await c.QueryAsync<(Guid ProjectId, string Serial)>(
            "SELECT pd.project_id, pd.serial FROM project_devices pd JOIN project_members m ON m.project_id = pd.project_id WHERE m.user_id = @userId ORDER BY pd.added_at",
            new { userId })).ToLookup(x => x.ProjectId, x => x.Serial);
        return projects.Select(p => (p, devices[p.Id].ToArray())).ToList();
    }
}

public sealed class AuditLog(Db db)
{
    public async Task WriteAsync(Guid? userId, string action, string? target, object? detail, string? ip, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct);
        await c.ExecuteAsync("INSERT INTO audit_log (user_id, action, target, detail, ip) VALUES (@userId, @action, @target, @detail::jsonb, @ip)",
            new { userId, action, target, detail = detail is null ? null : JsonSerializer.Serialize(detail, LiveJson.Options), ip });
    }
}

/// <summary>
/// Uploaded files (avatars, project photos, ticket attachments), by area and a name the store generates. Plan B3: the
/// local folder suits one instance; several instances need a shared store (B6).
/// </summary>
public interface IFileStore
{
    Task<string> SaveAsync(string area, byte[] data, string contentType, CancellationToken ct);

    /// <summary>The file, or null when the name is not one the store made or the file is gone.</summary>
    Task<Stream?> OpenAsync(string area, string? name, CancellationToken ct);

    Task DeleteAsync(string area, string? name, CancellationToken ct);
}

/// <summary>Checks and names for uploads: types are taken from the content, names are made by the server.</summary>
public static class FileStore
{
    /// <summary>Recognises JPEG, PNG and WebP by their magic bytes; the client's declared type is ignored.</summary>
    public static string? ImageType(ReadOnlySpan<byte> b) =>
        b.Length >= 3 && b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF ? "image/jpeg"
        : b.Length >= 8 && b[..8].SequenceEqual(new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }) ? "image/png"
        : b.Length >= 12 && b[..4].SequenceEqual("RIFF"u8) && b[8..12].SequenceEqual("WEBP"u8) ? "image/webp"
        : null;

    public static string? VideoType(ReadOnlySpan<byte> b) =>
        b.Length >= 12 && b[4..8].SequenceEqual("ftyp"u8) ? "video/mp4" : null;

    public static string NewName(string contentType) =>
        $"{Guid.NewGuid():N}" + (contentType switch { "image/jpeg" => ".jpg", "image/png" => ".png", "image/webp" => ".webp", "video/mp4" => ".mp4", _ => ".bin" });

    /// <summary>Names are generated by <see cref="NewName"/>; anything else (path separators, dots) is rejected.</summary>
    public static bool IsValidName([System.Diagnostics.CodeAnalysis.NotNullWhen(true)] string? name) =>
        !(string.IsNullOrEmpty(name) || name.Length > 48 || !name.All(ch => char.IsAsciiLetterOrDigit(ch) || ch == '.') || name.Count(ch => ch == '.') != 1);

    /// <summary>Image type from a stored name (avatars and project photos keep no separate type).</summary>
    public static string ImageTypeOf(string name) => name.EndsWith(".png") ? "image/png" : name.EndsWith(".webp") ? "image/webp" : "image/jpeg";

    public static async Task<byte[]?> ReadLimitedAsync(Stream s, int maxBytes, CancellationToken ct)
    {
        using var ms = new MemoryStream();
        var buf = new byte[81920];
        int n;
        while ((n = await s.ReadAsync(buf, ct)) > 0)
        {
            if (ms.Length + n > maxBytes) return null;
            ms.Write(buf, 0, n);
        }
        return ms.ToArray();
    }
}

/// <summary>Files in a local folder (Storage:Root). The default, and the right choice for a single instance.</summary>
public sealed class LocalFileStore(IConfiguration config) : IFileStore
{
    private readonly string _root = Path.GetFullPath(config["Storage:Root"] ?? "storage");

    public async Task<string> SaveAsync(string area, byte[] data, string contentType, CancellationToken ct)
    {
        var name = FileStore.NewName(contentType);
        var dir = Path.Combine(_root, area);
        Directory.CreateDirectory(dir);
        await File.WriteAllBytesAsync(Path.Combine(dir, name), data, ct);
        return name;
    }

    public Task<Stream?> OpenAsync(string area, string? name, CancellationToken ct)
    {
        if (PathOf(area, name) is not { } path) return Task.FromResult<Stream?>(null);
        return Task.FromResult<Stream?>(new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read, 81920, useAsync: true));
    }

    public Task DeleteAsync(string area, string? name, CancellationToken ct)
    {
        if (PathOf(area, name) is { } p) File.Delete(p);
        return Task.CompletedTask;
    }

    private string? PathOf(string area, string? name)
    {
        if (!FileStore.IsValidName(name)) return null;
        var path = Path.Combine(_root, area, name);
        return File.Exists(path) ? path : null;
    }
}
