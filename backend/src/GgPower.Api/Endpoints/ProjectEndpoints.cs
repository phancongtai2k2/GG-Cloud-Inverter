using Dapper;
using GgPower.Domain;
using GgPower.Infrastructure;
using Microsoft.Extensions.Options;
using Npgsql;

namespace GgPower.Api.Endpoints;

public static class ProjectEndpoints
{
    public sealed record SerialRequest(string? Serial);
    public sealed record TariffBody(string? Kind, long? FixedPerKwh, long? SellPerKwh);
    public sealed record CreateProject(
        string? Serial, string? Name, string? SiteType, string? Address, double? Latitude, double? Longitude,
        string? TimeZone, string? InstalledOn, double? PvKwp, TariffBody? Tariff);

    private static string NormalizeSerial(string? raw) => new string((raw ?? "").Where(ch => !char.IsWhiteSpace(ch)).ToArray()).ToUpperInvariant();

    private static double? Kw(double? w) => w is { } v ? Math.Round(v / 1000, 2) : null;

    public static void Map(RouteGroupBuilder api)
    {
        var g = api.MapGroup("/projects").RequireAuthorization();

        g.MapGet("", async (HttpContext ctx, AccessService access, ILiveStore live, StatsService stats, TimeProvider clock, CancellationToken ct) =>
        {
            var now = clock.GetUtcNow();
            var list = new List<object>();
            var projects = (await access.ProjectsOfAsync(ctx.User.UserId(), ct)).ToList();
            // One round trip for all projects when the live state is shared (C2).
            var states = await live.GetManyAsync(projects.Select(x => x.Item2.Length > 0 ? x.Item2[0] : "").ToArray(), ct);
            var index = 0;
            foreach (var (p, serials) in projects)
            {
                var d = states[index++];
                var reachable = d?.IsReachable(now) == true;
                var t = reachable ? d!.Telemetry : null;
                list.Add(new
                {
                    id = p.Id, name = p.Name, address = p.Address, site_type = p.SiteType, role = p.Role,
                    configuration = Configuration(d, serials.Length),
                    status = d is null ? "waiting_connection" : d.StatusName(now),
                    pv_power_kw = Kw(t?.PvW),
                    today_energy_kwh = t?.YieldToday,
                    battery_soc_percent = t?.Soc is { } s ? (int?)Math.Round(s) : null,
                });
            }
            return Results.Json(list);
        });

        // Step 1 of "add project": is this serial a device we know, and is it free?
        g.MapPost("/check-serial", async (SerialRequest r, HttpContext ctx, Db db, LiveStateCache live, CancellationToken ct) =>
        {
            var serial = NormalizeSerial(r.Serial);
            if (!UpTopic.IsSerial(serial)) return Api.NotFound("SERIAL_NOT_FOUND");
            await using var c = await db.OpenAsync(ct);
            var row = await c.QuerySingleOrDefaultAsync(
                "SELECT d.serial, d.model, d.rated_power_kw, (SELECT project_id FROM project_devices pd WHERE pd.serial = d.serial) AS project_id FROM devices d WHERE upper(d.serial) = @serial",
                new { serial });
            if (row is null) return Api.NotFound("SERIAL_NOT_FOUND");
            if (row.project_id is not null) return Api.Error(409, "SERIAL_OWNED");
            return Results.Json(new
            {
                serial = (string)row.serial, name = (string?)row.model ?? "Inverter", model = (string?)row.model,
                rated_power_kw = (double?)row.rated_power_kw, manufactured_on = (string?)null,
            });
        }).RequireRateLimiting("auth");

        g.MapPost("", async (CreateProject r, HttpContext ctx, Db db, AuditLog audit, IOptions<StatsOptions> stats, IOptions<ContentOptions> content, TimeProvider clock, CancellationToken ct) =>
        {
            var errors = new List<FieldError>();
            var name = (r.Name ?? "").Trim();
            if (name.Length is 0 or > 40) errors.Add(new("name", "NAME_INVALID"));
            if (r.SiteType is not ("home" or "factory")) errors.Add(new("site_type", "VALUE_INVALID"));
            var address = (r.Address ?? "").Trim();
            if (address.Length is 0 or > 300) errors.Add(new("address", "ADDRESS_INVALID"));
            if (r.Latitude is < -90 or > 90 || r.Longitude is < -180 or > 180 || (r.Latitude is null) != (r.Longitude is null)) errors.Add(new("coordinates", "VALUE_INVALID"));
            var zone = r.TimeZone ?? "Asia/Ho_Chi_Minh";
            try { TimeZoneInfo.FindSystemTimeZoneById(zone); } catch (Exception) { errors.Add(new("time_zone", "VALUE_INVALID")); }
            if (!DateOnly.TryParseExact(r.InstalledOn, "yyyy-MM-dd", out var installed) || installed > DateOnly.FromDateTime(clock.GetUtcNow().UtcDateTime.AddDays(1)))
                errors.Add(new("installed_on", "DATE_INVALID"));
            if (r.PvKwp is not (> 0 and <= 100000)) errors.Add(new("pv_kwp", "VALUE_INVALID"));
            var kind = r.Tariff?.Kind ?? "evn_tiered";
            if (kind is not ("evn_tiered" or "fixed")) errors.Add(new("tariff.kind", "VALUE_INVALID"));
            if (kind == "fixed" && r.Tariff?.FixedPerKwh is not (> 0 and <= 1_000_000)) errors.Add(new("tariff.fixed_per_kwh", "VALUE_INVALID"));
            if (r.Tariff?.SellPerKwh is < 0 or > 1_000_000) errors.Add(new("tariff.sell_per_kwh", "VALUE_INVALID"));
            var serial = NormalizeSerial(r.Serial);
            if (!UpTopic.IsSerial(serial)) errors.Add(new("serial", "SERIAL_INVALID"));
            if (errors.Count > 0) return Api.Invalid(errors);

            var userId = ctx.User.UserId();
            await using var c = await db.OpenAsync(ct);
            await using var tx = await c.BeginTransactionAsync(ct);
            if (await c.ExecuteScalarAsync<int>("SELECT count(*) FROM projects WHERE owner_id = @userId", new { userId }, tx) >= stats.Value.MaxProjectsPerUser)
                return Api.Error(409, "PROJECT_LIMIT");
            var device = await c.QuerySingleOrDefaultAsync("SELECT serial, model FROM devices WHERE upper(serial) = @serial FOR UPDATE", new { serial }, tx);
            if (device is null) return Api.NotFound("SERIAL_NOT_FOUND");

            var id = Guid.NewGuid();
            await c.ExecuteAsync(
                """
                INSERT INTO projects (id, owner_id, name, site_type, address, latitude, longitude, time_zone, installed_on, pv_kwp, tariff_kind, tariff_fixed, tariff_sell)
                VALUES (@id, @userId, @name, @site, @address, @lat, @lon, @zone, @installed, @kwp, @kind, @fixed, @sell)
                """,
                new { id, userId, name, site = r.SiteType, address, lat = r.Latitude, lon = r.Longitude, zone, installed, kwp = r.PvKwp, kind,
                      @fixed = kind == "fixed" ? r.Tariff?.FixedPerKwh : null, sell = r.Tariff?.SellPerKwh ?? 0 }, tx);
            await c.ExecuteAsync("INSERT INTO project_members (project_id, user_id) VALUES (@id, @userId)", new { id, userId }, tx);
            try
            {
                // The primary key on serial makes "one device, one project" hold even under a race.
                await c.ExecuteAsync("INSERT INTO project_devices (serial, project_id, name) VALUES (@serial, @id, @name)",
                    new { serial = (string)device.serial, id, name = (string?)device.model ?? "Inverter" }, tx);
            }
            catch (PostgresException ex) when (ex.SqlState == PostgresErrorCodes.UniqueViolation)
            {
                return Api.Error(409, "SERIAL_OWNED");
            }
            await c.ExecuteAsync(
                "INSERT INTO warranty_devices (id, project_id, name, serial, activated_on, expires_on) VALUES (@wid, @id, @name, @serial, @installed, @expires)",
                new { wid = Guid.NewGuid(), id, name = (string?)device.model ?? "Inverter", serial = (string)device.serial, installed,
                      expires = installed.AddYears(content.Value.InverterWarrantyYears) }, tx);
            await tx.CommitAsync(ct);
            await audit.WriteAsync(userId, "project_created", id.ToString(), new { serial }, ctx.ClientIp(), ct);
            return Results.Json(new { project_id = id }, statusCode: 201);
        });

        // Removes a project and frees its devices so they can be added again (by this or another account).
        // Measurements stay with the device serial; tickets keep their history without the project link.
        g.MapDelete("/{projectId:guid}", async (Guid projectId, HttpContext ctx, AccessService access, Db db, IFileStore files, AuditLog audit, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            if (a.Project.OwnerId != ctx.User.UserId()) return Api.Error(403, "ROLE_NOT_PERMITTED");
            await using var c = await db.OpenAsync(ct);
            await c.ExecuteAsync("DELETE FROM projects WHERE id = @projectId", new { projectId });
            await files.DeleteAsync("projects", a.Project.PhotoFile, ct);
            access.Invalidate(ctx.User.UserId(), projectId);
            await audit.WriteAsync(ctx.User.UserId(), "project_deleted", projectId.ToString(), new { serials = a.Serials }, ctx.ClientIp(), ct);
            return Results.NoContent();
        });

        g.MapPut("/{projectId:guid}/photo", async (Guid projectId, HttpContext ctx, AccessService access, Db db, IFileStore files, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            var data = await FileStore.ReadLimitedAsync(ctx.Request.Body, 5 * 1024 * 1024, ct);
            if (data is null) return Api.Error(413, "PAYLOAD_TOO_LARGE");
            if (FileStore.ImageType(data) is not { } type) return Api.Error(415, "UNSUPPORTED_MEDIA_TYPE");
            var name = await files.SaveAsync("projects", data, type, ct);
            await using var c = await db.OpenAsync(ct);
            await c.ExecuteAsync("UPDATE projects SET photo_file = @name WHERE id = @projectId", new { name, projectId });
            await files.DeleteAsync("projects", a.Project.PhotoFile, ct);
            access.Invalidate(ctx.User.UserId(), projectId);
            return Results.NoContent();
        }).WithMetadata(new Microsoft.AspNetCore.Mvc.RequestSizeLimitAttribute(5 * 1024 * 1024 + 1024));

        g.MapGet("/{projectId:guid}/photo", async (Guid projectId, HttpContext ctx, AccessService access, IFileStore files, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a || await files.OpenAsync("projects", a.Project.PhotoFile, ct) is not { } file) return Api.NotFound();
            return Results.File(file, FileStore.ImageTypeOf(a.Project.PhotoFile!));
        });
    }

    public static string Configuration(DeviceLive? d, int deviceCount)
    {
        if (d is null) return "";
        var model = d.Model ?? "Inverter";
        return deviceCount > 1 ? $"{deviceCount} × {model}" : model;
    }
}
