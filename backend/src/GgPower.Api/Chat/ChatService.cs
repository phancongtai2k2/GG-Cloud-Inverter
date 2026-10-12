using System.Globalization;
using System.Text.Json;
using Dapper;
using GgPower.Infrastructure;
using Microsoft.Extensions.Options;

namespace GgPower.Api.Chat;

public enum ChatOutcome { Answered, Refused, Disabled, ConversationNotFound, ConversationFull, QuotaExceeded, Busy, Unavailable }

public sealed record ChatAnswer(ChatOutcome Outcome, Guid? ConversationId = null, string? Answer = null);

/// <summary>
/// Answers one question: replays the conversation to the model, runs the tools it asks for, and stores the new turns.
/// A question is stored only once it has been answered, all of it in one transaction, so a failed or declined question
/// leaves the conversation as it was. Stored turns are never edited or trimmed: a full conversation is closed instead.
/// </summary>
public sealed class ChatService(ILlmClient llm, ChatTools tools, Db db, TimeProvider clock, IOptions<ChatOptions> options, ILogger<ChatService> log)
{
    public const int MaxQuestionLength = 2000;

    // Fixed text: the system prompt is cached and part of what every stored reasoning block was produced with. The
    // current time goes into each question instead.
    public const string SystemPrompt =
        """
        Bạn là trợ lý của ứng dụng GG Power, ứng dụng giám sát hệ thống điện mặt trời có inverter hybrid và pin lưu trữ.
        Người dùng đang xem một dự án (một công trình lắp đặt) của chính họ; các công cụ chỉ đọc dữ liệu của dự án đó.

        Cách trả lời:
        - Trả lời bằng ngôn ngữ người dùng dùng (mặc định tiếng Việt), ngắn gọn, thân thiện, dễ hiểu với người không chuyên.
        - Số liệu về hệ thống (công suất, sản lượng, pin, cảnh báo, thống kê, thời tiết) phải lấy từ công cụ, không được tự
          đặt ra. Ghi đơn vị (kW, kWh, %, °C, đồng). Nếu công cụ không có dữ liệu hoặc báo lỗi, nói rõ điều đó.
        - Mỗi câu hỏi có kèm thời điểm hiện tại ở dự án; dùng nó cho "hôm nay", "hôm qua", "tháng này"…
        - Bạn chỉ xem được dữ liệu, không thay đổi được cài đặt thiết bị. Khi người dùng muốn đổi chế độ, lịch sạc / xả hay
          SOC, hãy chỉ họ vào màn Cấu hình của thiết bị trong ứng dụng.
        - Với cảnh báo hay lỗi thiết bị, giải thích ý nghĩa và cách xử lý an toàn; việc liên quan đến điện áp cao, tủ điện
          hay mở thiết bị thì khuyên gọi kỹ thuật viên hoặc tổng đài hỗ trợ (có ở màn Hỗ trợ của ứng dụng).
        - Về luật, nghị định, thông tư về năng lượng ở Việt Nam: trả lời theo hiểu biết của bạn, nêu tên và số hiệu văn bản
          khi chắc chắn, nói rõ văn bản có thể đã được sửa đổi và người dùng nên kiểm tra bản hiện hành trên
          vbpl.vn hoặc thuvienphapluat.vn. Đây là thông tin tham khảo, không phải tư vấn pháp lý.
        - Từ chối nhẹ nhàng các yêu cầu không liên quan đến năng lượng, hệ thống điện mặt trời, thời tiết hay ứng dụng.
        """;

    private sealed record Row(int Seq, string Role, string Blocks);

    public async Task<ChatAnswer> AskAsync(Guid userId, ProjectAccess a, Guid? conversationId, string question, CancellationToken ct)
    {
        var o = options.Value;
        if (!llm.IsConfigured) return new(ChatOutcome.Disabled);

        var history = new List<ChatTurn>();
        var isNew = conversationId is null;
        var id = conversationId ?? Guid.NewGuid();
        // Not held open while the model answers.
        await using (var c = await db.OpenAsync(ct))
        {
            var asked = await c.ExecuteScalarAsync<int>(
                """
                SELECT count(*) FROM chat_messages m JOIN chat_conversations cv ON cv.id = m.conversation_id
                WHERE cv.user_id = @userId AND m.role = 'user' AND m.shown_text IS NOT NULL AND m.created_at > now() - interval '24 hours'
                """, new { userId });
            if (asked >= o.MaxQuestionsPerUserPerDay) return new(ChatOutcome.QuotaExceeded);
            if (!isNew)
            {
                var questions = await c.ExecuteScalarAsync<int?>(
                    "SELECT questions FROM chat_conversations WHERE id = @id AND user_id = @userId AND project_id = @projectId",
                    new { id, userId, projectId = a.Project.Id });
                if (questions is null) return new(ChatOutcome.ConversationNotFound);
                if (questions >= o.MaxQuestionsPerConversation) return new(ChatOutcome.ConversationFull);
                foreach (var r in await c.QueryAsync<Row>("SELECT seq, role, blocks::text AS blocks FROM chat_messages WHERE conversation_id = @id ORDER BY seq", new { id }))
                    history.Add(new ChatTurn(r.Role, JsonSerializer.Deserialize<List<ChatBlock>>(r.Blocks, ChatBlock.Json)!));
            }
        }
        var firstSeq = history.Count;

        var local = TimeZoneInfo.ConvertTime(clock.GetUtcNow(), a.Zone);
        // Weekday spelled out here: the container may run without culture data.
        string[] weekdays = ["Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"];
        var turns = new List<ChatTurn>(history)
        {
            new("user", [
                ChatBlock.OfText($"[Thời điểm hiện tại ở dự án: {weekdays[(int)local.DayOfWeek]}, {local.ToString("yyyy-MM-dd HH:mm", CultureInfo.InvariantCulture)} ({a.Project.TimeZone})]"),
                ChatBlock.OfText(question),
            ]),
        };
        var replies = new List<LlmReply>();
        string? answer = null;
        try
        {
            for (var call = 0; call < o.MaxModelCallsPerQuestion && answer is null; call++)
            {
                var reply = await llm.CompleteAsync(SystemPrompt, ChatTools.Specs, turns, ct);
                if (reply.StopReason == "refusal") return new(ChatOutcome.Refused, isNew ? null : id);
                replies.Add(reply);
                turns.Add(new ChatTurn("assistant", reply.Blocks));
                var uses = reply.Blocks.Where(b => b.Type == "tool_use").ToList();
                if (reply.StopReason == "tool_use" && uses.Count > 0)
                {
                    var results = new List<ChatBlock>();
                    foreach (var use in uses)
                    {
                        var (result, isError) = await tools.RunAsync(a, use.Name!, use.Input, ct);
                        results.Add(new ChatBlock { Type = "tool_result", ToolUseId = use.Id, Text = result, IsError = isError ? true : null });
                    }
                    turns.Add(new ChatTurn("user", results));
                    continue;
                }
                // A tool call cut off by the length limit has no result to pair with: the question is not stored.
                if (uses.Count > 0) break;
                answer = string.Join("\n\n", reply.Blocks.Where(b => b.Type == "text" && !string.IsNullOrWhiteSpace(b.Text)).Select(b => b.Text!.Trim()));
            }
        }
        catch (Exception e) when (e is not OperationCanceledException)
        {
            log.LogWarning(e, "chat model call failed");
            return new(ChatOutcome.Unavailable, isNew ? null : id);
        }
        if (answer is null) return new(ChatOutcome.Unavailable, isNew ? null : id);
        if (answer.Length == 0) answer = "Xin lỗi, mình chưa trả lời được câu này. Bạn thử hỏi lại theo cách khác nhé.";

        await using var c2 = await db.OpenAsync(ct);
        await using var tx = await c2.BeginTransactionAsync(ct);
        try
        {
            if (isNew)
                await c2.ExecuteAsync(
                    "INSERT INTO chat_conversations (id, user_id, project_id, title) VALUES (@id, @userId, @projectId, @title)",
                    new { id, userId, projectId = a.Project.Id, title = Title(question) }, tx);
            var replyIndex = 0;
            for (var i = firstSeq; i < turns.Count; i++)
            {
                var t = turns[i];
                var r = t.Role == "assistant" ? replies[replyIndex++] : null;
                var shown = i == firstSeq ? question : i == turns.Count - 1 ? answer : null;
                await c2.ExecuteAsync(
                    """
                    INSERT INTO chat_messages (conversation_id, seq, role, blocks, shown_text, model, input_tokens, output_tokens)
                    VALUES (@id, @seq, @role, @blocks::jsonb, @shown, @model, @inTokens, @outTokens)
                    """,
                    new
                    {
                        id, seq = i, role = t.Role, blocks = JsonSerializer.Serialize(t.Blocks, ChatBlock.Json), shown,
                        model = r?.Model, inTokens = (int?)r?.InputTokens, outTokens = (int?)r?.OutputTokens,
                    }, tx);
            }
            await c2.ExecuteAsync("UPDATE chat_conversations SET questions = questions + 1, updated_at = now() WHERE id = @id", new { id }, tx);
            await tx.CommitAsync(ct);
        }
        catch (Npgsql.PostgresException e) when (e.SqlState == Npgsql.PostgresErrorCodes.UniqueViolation)
        {
            // Another question of the same conversation was stored meanwhile.
            return new(ChatOutcome.Busy, id);
        }
        return new(ChatOutcome.Answered, id, answer);
    }

    private static string Title(string question)
    {
        var line = question.Trim().Split('\n')[0].Trim();
        return line.Length <= 60 ? line : line[..57].TrimEnd() + "…";
    }
}
