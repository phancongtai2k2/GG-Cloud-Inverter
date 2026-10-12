using System.Text.Json.Nodes;
using Dapper;
using GgPower.Domain;
using GgPower.Infrastructure;
using Microsoft.Extensions.Options;

namespace GgPower.Api.Endpoints;

/// <summary>Support contact, explore content, support tickets and warranty.</summary>
public static class ServiceEndpoints
{
    public sealed record NewTicket(string? Type, string? Title, string? Description, Guid? ProjectId, Guid? AlertId);
    public sealed record NewMessage(string? Text);
    public sealed record StatusRequest(string? Status);

    private const int MaxAttachments = 5;
    private const int MaxImageBytes = 10 * 1024 * 1024;
    private const int MaxVideoBytes = 50 * 1024 * 1024;

    private static object TicketJson(dynamic r) => new
    {
        id = (Guid)r.id, code = (string)r.code, title = (string)r.title, type = (string)r.type,
        project_id = (Guid?)r.project_id, project_name = (string?)r.project_name,
        created_at = (DateTime)r.created_at, status = (string)r.status,
    };

    public static void Map(RouteGroupBuilder api)
    {
        var auth = api.MapGroup("").RequireAuthorization();

        auth.MapGet("/support/contact", (HttpContext ctx, IOptions<ContentOptions> o) =>
            Api.Cached(ctx, new { hotline = o.Value.Hotline, email = o.Value.SupportEmail }, maxAgeSeconds: 3600));

        // Explore content is an editable JSON file until a CMS exists; it is served with an ETag.
        auth.MapGet("/explore", async (HttpContext ctx, IOptions<ContentOptions> o, IWebHostEnvironment env, CancellationToken ct) =>
        {
            var path = o.Value.ExploreFile ?? Path.Combine(env.ContentRootPath, "Content", "explore.json");
            if (!File.Exists(path)) return Api.Cached(ctx, new { products = Array.Empty<object>(), articles = Array.Empty<object>(), videos = Array.Empty<object>() }, maxAgeSeconds: 300);
            var node = JsonNode.Parse(await File.ReadAllTextAsync(path, ct));
            return Api.Cached(ctx, node!, maxAgeSeconds: 300);
        });

        // ---------- support tickets (always scoped to the caller) ----------

        auth.MapGet("/tickets", async (HttpContext ctx, Db db, CancellationToken ct) =>
        {
            await using var c = await db.OpenAsync(ct);
            var rows = await c.QueryAsync(
                "SELECT t.id, t.code, t.title, t.type, t.project_id, p.name AS project_name, t.created_at, t.status FROM tickets t LEFT JOIN projects p ON p.id = t.project_id WHERE t.user_id = @u ORDER BY t.created_at DESC LIMIT 200",
                new { u = ctx.User.UserId() });
            return Api.Cached(ctx, rows.Select(r => (object)TicketJson(r)));
        });

        auth.MapPost("/tickets", async (NewTicket r, HttpContext ctx, AccessService access, Db db, TimeProvider clock, CancellationToken ct) =>
        {
            var errors = new List<FieldError>();
            var title = (r.Title ?? "").Trim();
            var description = (r.Description ?? "").Trim();
            if (r.Type is not ("technical" or "account" or "other")) errors.Add(new("type", "VALUE_INVALID"));
            if (title.Length is 0 or > 120) errors.Add(new("title", "TITLE_INVALID"));
            if (description.Length is 0 or > 4000) errors.Add(new("description", "DESCRIPTION_INVALID"));
            if (errors.Count > 0) return Api.Invalid(errors);

            ProjectAccess? a = null;
            if (r.ProjectId is { } pid && (a = await access.ProjectAsync(ctx.User, pid, ct)) is null) return Api.NotFound("PROJECT_NOT_FOUND");
            await using var c = await db.OpenAsync(ct);
            Guid? alertId = null;
            if (r.AlertId is { } aid && a is not null)
                alertId = await c.ExecuteScalarAsync<Guid?>("SELECT id FROM alerts WHERE id = @aid AND serial = ANY(@serials)", new { aid, serials = a.Serials });

            var local = TimeZoneInfo.ConvertTime(clock.GetUtcNow(), a?.Zone ?? CommandService.Zone("Asia/Ho_Chi_Minh"));
            var seq = await c.ExecuteScalarAsync<long>("SELECT nextval('ticket_seq')");
            var id = Guid.NewGuid();
            var code = $"#HT-{local:ddMM}-{seq:D4}";
            await c.ExecuteAsync(
                "INSERT INTO tickets (id, code, user_id, project_id, alert_id, type, title, description) VALUES (@id, @code, @u, @p, @al, @type, @title, @description)",
                new { id, code, u = ctx.User.UserId(), p = r.ProjectId, al = alertId, type = r.Type, title, description });
            return Results.Json(new
            {
                id, code, title, type = r.Type, project_id = r.ProjectId, project_name = a?.Project.Name, created_at = clock.GetUtcNow(), status = "received",
            }, statusCode: 201);
        });

        auth.MapGet("/tickets/{ticketId:guid}", async (Guid ticketId, HttpContext ctx, Db db, CancellationToken ct) =>
        {
            await using var c = await db.OpenAsync(ct);
            var t = await c.QuerySingleOrDefaultAsync(
                "SELECT t.id, t.code, t.title, t.type, t.project_id, p.name AS project_name, t.created_at, t.status, t.description FROM tickets t LEFT JOIN projects p ON p.id = t.project_id WHERE t.id = @ticketId AND t.user_id = @u",
                new { ticketId, u = ctx.User.UserId() });
            if (t is null) return Api.NotFound("TICKET_NOT_FOUND");
            var messages = await c.QueryAsync("SELECT id, author, body, created_at FROM ticket_messages WHERE ticket_id = @ticketId ORDER BY created_at", new { ticketId });
            var attachments = await c.QueryAsync("SELECT id, content_type, size_bytes FROM ticket_attachments WHERE ticket_id = @ticketId ORDER BY created_at", new { ticketId });
            return Api.Cached(ctx, new
            {
                summary = (object)TicketJson(t),
                description = (string)t.description,
                attachments = attachments.Select(x => new { id = (Guid)x.id, content_type = (string)x.content_type, size_bytes = (long)x.size_bytes }),
                messages = messages.Select(m => new { id = (Guid)m.id, author = (string)m.author, text = (string)m.body, created_at = (DateTime)m.created_at }),
            });
        });

        auth.MapPost("/tickets/{ticketId:guid}/messages", async (Guid ticketId, NewMessage r, HttpContext ctx, Db db, TimeProvider clock, CancellationToken ct) =>
        {
            var text = (r.Text ?? "").Trim();
            if (text.Length is 0 or > 2000) return Api.Invalid();
            await using var c = await db.OpenAsync(ct);
            var status = await c.ExecuteScalarAsync<string?>("SELECT status FROM tickets WHERE id = @ticketId AND user_id = @u", new { ticketId, u = ctx.User.UserId() });
            if (status is null) return Api.NotFound("TICKET_NOT_FOUND");
            if (status == "closed") return Api.Error(409, "TICKET_CLOSED");
            // A new message from the user reopens a ticket that was marked resolved.
            if (status == "resolved") await c.ExecuteAsync("UPDATE tickets SET status = 'processing' WHERE id = @ticketId", new { ticketId });
            var id = Guid.NewGuid();
            var now = clock.GetUtcNow();
            await c.ExecuteAsync("INSERT INTO ticket_messages (id, ticket_id, author, body, created_at) VALUES (@id, @ticketId, 'user', @text, @now)", new { id, ticketId, text, now });
            return Results.Json(new { id, author = "user", text, created_at = now }, statusCode: 201);
        });

        // One file per request, raw body. The type is decided from the content, not from the client's header.
        auth.MapPost("/tickets/{ticketId:guid}/attachments", async (Guid ticketId, HttpContext ctx, Db db, IFileStore files, CancellationToken ct) =>
        {
            await using var c = await db.OpenAsync(ct);
            if (!await c.ExecuteScalarAsync<bool>("SELECT EXISTS (SELECT 1 FROM tickets WHERE id = @ticketId AND user_id = @u)", new { ticketId, u = ctx.User.UserId() }))
                return Api.NotFound("TICKET_NOT_FOUND");
            if (await c.ExecuteScalarAsync<int>("SELECT count(*) FROM ticket_attachments WHERE ticket_id = @ticketId", new { ticketId }) >= MaxAttachments)
                return Api.Error(409, "ATTACHMENT_LIMIT");
            var data = await FileStore.ReadLimitedAsync(ctx.Request.Body, MaxVideoBytes, ct);
            if (data is null) return Api.Error(413, "PAYLOAD_TOO_LARGE");
            var type = FileStore.ImageType(data) ?? FileStore.VideoType(data);
            if (type is null) return Api.Error(415, "UNSUPPORTED_MEDIA_TYPE");
            if (type.StartsWith("image/") && data.Length > MaxImageBytes) return Api.Error(413, "PAYLOAD_TOO_LARGE");
            var stored = await files.SaveAsync("tickets", data, type, ct);
            var id = Guid.NewGuid();
            await c.ExecuteAsync("INSERT INTO ticket_attachments (id, ticket_id, content_type, size_bytes, stored_name) VALUES (@id, @ticketId, @type, @size, @stored)",
                new { id, ticketId, type, size = (long)data.Length, stored });
            return Results.Json(new { id, content_type = type, size_bytes = data.Length }, statusCode: 201);
        }).WithMetadata(new Microsoft.AspNetCore.Mvc.RequestSizeLimitAttribute(MaxVideoBytes + 1024));

        auth.MapGet("/tickets/{ticketId:guid}/attachments/{attachmentId:guid}", async (Guid ticketId, Guid attachmentId, HttpContext ctx, Db db, IFileStore files, CancellationToken ct) =>
        {
            await using var c = await db.OpenAsync(ct);
            var r = await c.QuerySingleOrDefaultAsync(
                "SELECT a.stored_name, a.content_type FROM ticket_attachments a JOIN tickets t ON t.id = a.ticket_id WHERE a.id = @attachmentId AND t.id = @ticketId AND t.user_id = @u",
                new { attachmentId, ticketId, u = ctx.User.UserId() });
            if (r is null || await files.OpenAsync("tickets", (string)r.stored_name, ct) is not { } file) return Api.NotFound();
            ctx.Response.Headers["Content-Disposition"] = "attachment";
            return Results.File(file, (string)r.content_type);
        });

        // ---------- support staff: answer and close tickets ----------

        var staff = api.MapGroup("/admin/tickets").RequireAuthorization("staff");

        staff.MapGet("", async (string? status, Db db, CancellationToken ct) =>
        {
            if (status is not (null or "received" or "processing" or "resolved" or "closed")) return Api.Invalid();
            await using var c = await db.OpenAsync(ct);
            var rows = await c.QueryAsync(
                """
                SELECT t.id, t.code, t.title, t.type, t.project_id, p.name AS project_name, t.created_at, t.status, u.name AS user_name, u.phone AS user_phone
                FROM tickets t JOIN users u ON u.id = t.user_id LEFT JOIN projects p ON p.id = t.project_id
                WHERE (@status::text IS NULL OR t.status = @status) ORDER BY t.created_at DESC LIMIT 500
                """, new { status });
            return Results.Json(rows.Select(r => new { ticket = (object)TicketJson(r), user_name = (string)r.user_name, user_phone = (string)r.user_phone }));
        });

        staff.MapGet("/{ticketId:guid}", async (Guid ticketId, Db db, CancellationToken ct) =>
        {
            await using var c = await db.OpenAsync(ct);
            var t = await c.QuerySingleOrDefaultAsync(
                "SELECT t.id, t.code, t.title, t.type, t.project_id, p.name AS project_name, t.created_at, t.status, t.description, t.alert_id FROM tickets t LEFT JOIN projects p ON p.id = t.project_id WHERE t.id = @ticketId",
                new { ticketId });
            if (t is null) return Api.NotFound("TICKET_NOT_FOUND");
            var messages = await c.QueryAsync("SELECT id, author, body, created_at FROM ticket_messages WHERE ticket_id = @ticketId ORDER BY created_at", new { ticketId });
            return Results.Json(new
            {
                summary = (object)TicketJson(t), description = (string)t.description, alert_id = (Guid?)t.alert_id,
                messages = messages.Select(m => new { id = (Guid)m.id, author = (string)m.author, text = (string)m.body, created_at = (DateTime)m.created_at }),
            });
        });

        staff.MapPost("/{ticketId:guid}/messages", async (Guid ticketId, NewMessage r, HttpContext ctx, Db db, AuditLog audit, CancellationToken ct) =>
        {
            var text = (r.Text ?? "").Trim();
            if (text.Length is 0 or > 2000) return Api.Invalid();
            await using var c = await db.OpenAsync(ct);
            // The first answer moves a new ticket to "processing".
            if (await c.ExecuteAsync("UPDATE tickets SET status = CASE WHEN status = 'received' THEN 'processing' ELSE status END WHERE id = @ticketId", new { ticketId }) == 0)
                return Api.NotFound("TICKET_NOT_FOUND");
            var id = Guid.NewGuid();
            await c.ExecuteAsync("INSERT INTO ticket_messages (id, ticket_id, author, body) VALUES (@id, @ticketId, 'support', @text)", new { id, ticketId, text });
            await audit.WriteAsync(ctx.User.UserId(), "ticket_reply", ticketId.ToString(), null, ctx.ClientIp(), ct);
            return Results.Json(new { id, author = "support", text }, statusCode: 201);
        });

        staff.MapPut("/{ticketId:guid}/status", async (Guid ticketId, StatusRequest r, HttpContext ctx, Db db, AuditLog audit, CancellationToken ct) =>
        {
            if (r.Status is not ("received" or "processing" or "resolved" or "closed")) return Api.Invalid();
            await using var c = await db.OpenAsync(ct);
            if (await c.ExecuteAsync("UPDATE tickets SET status = @Status WHERE id = @ticketId", new { r.Status, ticketId }) == 0) return Api.NotFound("TICKET_NOT_FOUND");
            await audit.WriteAsync(ctx.User.UserId(), "ticket_status", ticketId.ToString(), new { r.Status }, ctx.ClientIp(), ct);
            return Results.NoContent();
        });

        // ---------- warranty ----------

        auth.MapGet("/projects/{projectId:guid}/warranty", async (Guid projectId, HttpContext ctx, AccessService access, Db db, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            await using var c = await db.OpenAsync(ct);
            var devices = await c.QueryAsync("SELECT name, serial, activated_on, expires_on FROM warranty_devices WHERE project_id = @projectId ORDER BY activated_on, name", new { projectId });
            var history = await c.QueryAsync("SELECT title, device, work_kind, work_ref, on_date, channel, status FROM warranty_history WHERE project_id = @projectId ORDER BY on_date DESC", new { projectId });
            static string D(object d) => (d is DateOnly o ? o : DateOnly.FromDateTime((DateTime)d)).ToString("yyyy-MM-dd");
            return Api.Cached(ctx, new
            {
                project_name = a.Project.Name,
                devices = devices.Select(d => new { name = (string)d.name, serial = (string)d.serial, activated_on = D(d.activated_on), expires_on = D(d.expires_on) }),
                history = history.Select(h => new
                {
                    title = (string)h.title, device = (string)h.device, work_kind = (string)h.work_kind, work_ref = (string?)h.work_ref,
                    date = D(h.on_date), channel = (string)h.channel, status = (string)h.status,
                }),
            }, maxAgeSeconds: 300);
        });
    }
}
