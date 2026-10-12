using Dapper;
using GgPower.Api.Chat;
using GgPower.Infrastructure;
using Microsoft.Extensions.Options;

namespace GgPower.Api.Endpoints;

/// <summary>The chat assistant of a project: questions about the user's own devices, the weather, energy regulations.</summary>
public static class ChatEndpoints
{
    public sealed record AskRequest(string? Text, Guid? ConversationId);

    private sealed class ConversationRow
    {
        public string Title { get; set; } = "";
        public int Questions { get; set; }
    }

    public static void Map(RouteGroupBuilder api)
    {
        var g = api.MapGroup("/projects/{projectId:guid}/chat").RequireAuthorization();

        g.MapPost("/messages", async (Guid projectId, AskRequest req, HttpContext ctx, AccessService access, ChatService chat, IOptions<ChatOptions> options, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            var text = req.Text?.Trim() ?? "";
            if (text.Length is 0 or > ChatService.MaxQuestionLength) return Api.Invalid(new[] { new GgPower.Domain.FieldError("text", "VALUE_INVALID") });
            var r = await chat.AskAsync(ctx.User.UserId(), a, req.ConversationId, text, ct);
            return r.Outcome switch
            {
                ChatOutcome.Answered => Results.Json(new { conversation_id = r.ConversationId, answer = r.Answer, refused = false }),
                // Declined by the model's safety checks: not stored; the app shows its own wording.
                ChatOutcome.Refused => Results.Json(new { conversation_id = r.ConversationId, answer = (string?)null, refused = true }),
                ChatOutcome.Disabled => Api.Error(503, "CHAT_DISABLED"),
                ChatOutcome.ConversationNotFound => Api.NotFound("CONVERSATION_NOT_FOUND"),
                ChatOutcome.ConversationFull => Api.Error(409, "CONVERSATION_FULL", new { max_questions = options.Value.MaxQuestionsPerConversation }),
                ChatOutcome.Busy => Api.Error(409, "CONVERSATION_BUSY"),
                ChatOutcome.QuotaExceeded => Api.Error(429, "CHAT_QUOTA_EXCEEDED", new { max_questions_per_day = options.Value.MaxQuestionsPerUserPerDay }),
                _ => Api.Error(502, "CHAT_UNAVAILABLE"),
            };
        });

        g.MapGet("/conversations", async (Guid projectId, HttpContext ctx, AccessService access, Db db, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            await using var c = await db.OpenAsync(ct);
            var rows = await c.QueryAsync<(Guid Id, string Title, int Questions, DateTime UpdatedAt)>(
                "SELECT id, title, questions, updated_at FROM chat_conversations WHERE user_id = @userId AND project_id = @projectId ORDER BY updated_at DESC LIMIT 50",
                new { userId = ctx.User.UserId(), projectId });
            return Results.Json(rows.Select(r => new { id = r.Id, title = r.Title, questions = r.Questions, updated_at_utc = r.UpdatedAt }));
        });

        g.MapGet("/conversations/{conversationId:guid}", async (Guid projectId, Guid conversationId, HttpContext ctx, AccessService access, Db db, IOptions<ChatOptions> options, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            await using var c = await db.OpenAsync(ct);
            var cv = await c.QuerySingleOrDefaultAsync<ConversationRow>(
                "SELECT title, questions FROM chat_conversations WHERE id = @conversationId AND user_id = @userId AND project_id = @projectId",
                new { conversationId, userId = ctx.User.UserId(), projectId });
            if (cv is null) return Api.NotFound("CONVERSATION_NOT_FOUND");
            var messages = await c.QueryAsync<(string Role, string Text, DateTime CreatedAt)>(
                "SELECT role, shown_text, created_at FROM chat_messages WHERE conversation_id = @conversationId AND shown_text IS NOT NULL ORDER BY seq",
                new { conversationId });
            return Results.Json(new
            {
                id = conversationId, title = cv.Title, questions = cv.Questions,
                can_continue = cv.Questions < options.Value.MaxQuestionsPerConversation,
                messages = messages.Select(m => new { role = m.Role, text = m.Text, created_at_utc = m.CreatedAt }),
            });
        });

        g.MapDelete("/conversations/{conversationId:guid}", async (Guid projectId, Guid conversationId, HttpContext ctx, AccessService access, Db db, CancellationToken ct) =>
        {
            if (await access.ProjectAsync(ctx.User, projectId, ct) is not { } a) return Api.NotFound();
            await using var c = await db.OpenAsync(ct);
            var n = await c.ExecuteAsync("DELETE FROM chat_conversations WHERE id = @conversationId AND user_id = @userId AND project_id = @projectId",
                new { conversationId, userId = ctx.User.UserId(), projectId });
            return n == 0 ? Api.NotFound("CONVERSATION_NOT_FOUND") : Results.NoContent();
        });
    }
}
