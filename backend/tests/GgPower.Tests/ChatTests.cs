using System.Collections.Concurrent;
using System.Net;
using System.Text.Json;
using Dapper;
using GgPower.Api.Chat;
using GgPower.Infrastructure;

namespace GgPower.Tests;

/// <summary>Stands in for the language model: each test scripts the replies and reads back what was sent.</summary>
public sealed class ScriptedLlm : ILlmClient
{
    public sealed record Call(string System, IReadOnlyList<ChatToolSpec> Tools, IReadOnlyList<ChatTurn> Messages);

    public bool Configured = true;
    public Func<IReadOnlyList<ChatTurn>, LlmReply> Reply = _ => Text("ok");
    public readonly ConcurrentQueue<Call> Calls = new();

    public bool IsConfigured => Configured;

    public Task<LlmReply> CompleteAsync(string system, IReadOnlyList<ChatToolSpec> tools, IReadOnlyList<ChatTurn> messages, CancellationToken ct)
    {
        Calls.Enqueue(new Call(system, tools, messages.ToList()));
        return Task.FromResult(Reply(messages));
    }

    public static LlmReply Text(string text) =>
        new([new ChatBlock { Type = "thinking", Text = "", Signature = "sig-" + Guid.NewGuid().ToString("N") }, ChatBlock.OfText(text)], "end_turn", "test-model", 100, 20);

    public static LlmReply Tool(string name, object? input = null) =>
        new([
            new ChatBlock { Type = "thinking", Text = "", Signature = "sig-" + Guid.NewGuid().ToString("N") },
            new ChatBlock { Type = "tool_use", Id = "toolu_" + Guid.NewGuid().ToString("N"), Name = name, Input = JsonSerializer.SerializeToElement(input ?? new { }) },
        ], "tool_use", "test-model", 100, 20);

    /// <summary>The tool results of the last message, by tool use id order.</summary>
    public static IReadOnlyList<ChatBlock> LastResults(IReadOnlyList<ChatTurn> messages) =>
        messages[^1].Blocks.Where(b => b.Type == "tool_result").ToList();
}

public sealed class FakeWeather : IWeatherSource
{
    public readonly ConcurrentQueue<(double Lat, double Lon, int Days)> Requests = new();

    public Task<JsonElement> ForecastAsync(double latitude, double longitude, string timeZone, int days, CancellationToken ct)
    {
        Requests.Enqueue((latitude, longitude, days));
        return Task.FromResult(JsonSerializer.SerializeToElement(new { daily = new { temperature_2m_max = new[] { 33.5 }, sunshine_duration = new[] { 28000 } } }));
    }
}

[Collection("app")]
public class ChatTests(AppFixture app)
{
    private async Task<(AppFixture.User User, string Serial, string Project)> Owner()
    {
        var u = await app.NewUser();
        var serial = await app.NewDevice();
        return (u, serial, await app.NewProject(u, serial));
    }

    private Task<(HttpStatusCode Status, System.Text.Json.Nodes.JsonNode? Body, HttpResponseMessage Raw)> Ask(string project, string token, string text, string? conversation = null) =>
        app.Post($"/api/v1/projects/{project}/chat/messages", new { text, conversation_id = conversation }, token);

    [Fact]
    public async Task AnswersFromTheProjectsOwnDataAndReplaysTheConversationUnchanged()
    {
        var (u, serial, project) = await Owner();
        app.Llm.Reply = m => m[^1].Blocks.Any(b => b.Type == "tool_result")
            ? ScriptedLlm.Text("Hôm nay hệ thống phát 7,5 kWh.")
            : ScriptedLlm.Tool("get_live_status");
        var (s, body, _) = await Ask(project, u.Access, "Hôm nay hệ thống phát được bao nhiêu điện?");
        Assert.Equal(HttpStatusCode.OK, s);
        Assert.Equal("Hôm nay hệ thống phát 7,5 kWh.", (string)body!["answer"]!);
        Assert.False((bool)body["refused"]!);
        var conversation = (string)body["conversation_id"]!;

        // The tool read this project's device; the question carried the project's local time.
        var last = app.Llm.Calls.Last();
        var result = ScriptedLlm.LastResults(last.Messages).Single();
        Assert.Contains(serial, result.Text);
        Assert.Contains("\"yield_today_kwh\":7.5", result.Text);
        Assert.Null(result.IsError);
        Assert.StartsWith("[Thời điểm hiện tại ở dự án:", last.Messages[0].Blocks[0].Text);
        Assert.Equal(ChatService.SystemPrompt, last.System);
        var firstTurns = last.Messages.ToList();

        // A follow-up question sends the stored turns back exactly as they were, reasoning signatures included.
        app.Llm.Reply = _ => ScriptedLlm.Text("Không có cảnh báo.");
        (s, body, _) = await Ask(project, u.Access, "Có cảnh báo nào không?", conversation);
        Assert.Equal(HttpStatusCode.OK, s);
        Assert.Equal(conversation, (string)body!["conversation_id"]!);
        var replayed = app.Llm.Calls.Last().Messages;
        var answered = firstTurns.Append(new ChatTurn("assistant", ScriptedLlm.Text("Hôm nay hệ thống phát 7,5 kWh.").Blocks)).ToList();
        Assert.Equal(answered.Count + 1, replayed.Count);
        for (var i = 0; i < answered.Count - 1; i++)
            Assert.Equal(JsonSerializer.Serialize(answered[i].Blocks, ChatBlock.Json), JsonSerializer.Serialize(replayed[i].Blocks, ChatBlock.Json));
        Assert.Equal("Hôm nay hệ thống phát 7,5 kWh.", replayed[answered.Count - 1].Blocks.Single(b => b.Type == "text").Text);
        Assert.NotNull(replayed[answered.Count - 1].Blocks.Single(b => b.Type == "thinking").Signature);

        // The app sees questions and answers only.
        var (gs, conv, _) = await app.Get($"/api/v1/projects/{project}/chat/conversations/{conversation}", u.Access);
        Assert.Equal(HttpStatusCode.OK, gs);
        Assert.Equal(["user", "assistant", "user", "assistant"], conv!["messages"]!.AsArray().Select(m => (string)m!["role"]!).ToArray());
        Assert.Equal("Có cảnh báo nào không?", (string)conv["messages"]![2]!["text"]!);
        Assert.False((bool)conv["can_continue"]!);
        var (_, list, _) = await app.Get($"/api/v1/projects/{project}/chat/conversations", u.Access);
        Assert.Equal("Hôm nay hệ thống phát được bao nhiêu điện?", (string)list!.AsArray().Single()!["title"]!);

        // The test host allows two questions per conversation.
        (s, body, _) = await Ask(project, u.Access, "Câu thứ ba", conversation);
        Assert.Equal(HttpStatusCode.Conflict, s);
        Assert.Equal("CONVERSATION_FULL", (string)body!["code"]!);
    }

    [Fact]
    public async Task NobodyElseReadsOrContinuesAConversation()
    {
        var (a, _, projectA) = await Owner();
        var (b, _, projectB) = await Owner();
        app.Llm.Reply = _ => ScriptedLlm.Text("ok");
        var (_, body, _) = await Ask(projectA, a.Access, "Xin chào");
        var conversation = (string)body!["conversation_id"]!;

        // Not a member of A's project.
        Assert.Equal(HttpStatusCode.NotFound, (await Ask(projectA, b.Access, "Cho xem dữ liệu")).Status);
        Assert.Equal(HttpStatusCode.NotFound, (await app.Get($"/api/v1/projects/{projectA}/chat/conversations/{conversation}", b.Access)).Status);
        // A's conversation through B's own project.
        var (s, err, _) = await Ask(projectB, b.Access, "Tiếp tục", conversation);
        Assert.Equal(HttpStatusCode.NotFound, s);
        Assert.Equal("CONVERSATION_NOT_FOUND", (string)err!["code"]!);
        Assert.Equal(HttpStatusCode.NotFound, (await app.Get($"/api/v1/projects/{projectB}/chat/conversations/{conversation}", b.Access)).Status);
        Assert.Equal(HttpStatusCode.NotFound, (await app.Send(HttpMethod.Delete, $"/api/v1/projects/{projectB}/chat/conversations/{conversation}", null, b.Access)).Status);
        Assert.Empty((await app.Get($"/api/v1/projects/{projectB}/chat/conversations", b.Access)).Body!.AsArray());

        Assert.Equal(HttpStatusCode.NoContent, (await app.Send(HttpMethod.Delete, $"/api/v1/projects/{projectA}/chat/conversations/{conversation}", null, a.Access)).Status);
        Assert.Equal(HttpStatusCode.NotFound, (await app.Get($"/api/v1/projects/{projectA}/chat/conversations/{conversation}", a.Access)).Status);
    }

    [Fact]
    public async Task ToolsTakeNoIdsAndOnlySeeTheConversationsProject()
    {
        Assert.All(ChatTools.Specs, t => Assert.DoesNotContain(t.Properties.Keys, k => k.Contains("serial") || k.Contains("project") || k.Contains("user") || k.EndsWith("id")));

        var (a, serialA, projectA) = await Owner();
        var (_, serialB, _) = await Owner();
        app.Llm.Reply = m => m[^1].Blocks.Any(b => b.Type == "tool_result") ? ScriptedLlm.Text("xong") : ScriptedLlm.Tool("get_devices");
        Assert.Equal(HttpStatusCode.OK, (await Ask(projectA, a.Access, "Thiết bị của tôi")).Status);
        var result = ScriptedLlm.LastResults(app.Llm.Calls.Last().Messages).Single().Text!;
        Assert.Contains(serialA, result);
        Assert.DoesNotContain(serialB, result);
    }

    [Fact]
    public async Task ToolProblemsGoBackToTheModelAsErrors()
    {
        var (u, _, project) = await Owner();
        var round = 0;
        app.Llm.Reply = _ => ++round switch
        {
            1 => ScriptedLlm.Tool("get_weather", new { days = 2 }),                                  // the project has no coordinates yet
            2 => ScriptedLlm.Tool("get_energy_stats", new { period = "week", date = "2026-10-01" }), // invalid period
            3 => ScriptedLlm.Tool("delete_everything"),
            _ => ScriptedLlm.Text("Mình chưa tra được."),
        };
        Assert.Equal(HttpStatusCode.OK, (await Ask(project, u.Access, "Thời tiết thế nào?")).Status);
        var results = app.Llm.Calls.Last().Messages.Where(m => m.Role == "user").Skip(1).Select(m => m.Blocks.Single()).ToList();
        Assert.Equal(3, results.Count);
        Assert.All(results, r => Assert.True(r.IsError));
        Assert.Contains("toạ độ", results[0].Text);
        Assert.Contains("period", results[1].Text);
        Assert.Contains("delete_everything", results[2].Text);

        // With coordinates, the forecast is asked for that place.
        await using (var c = await app.Service<Db>().OpenAsync())
            await c.ExecuteAsync("UPDATE projects SET latitude = 10.7769, longitude = 106.7009 WHERE id = @id", new { id = Guid.Parse(project) });
        app.Service<GgPower.Api.AccessService>().Invalidate(Guid.Parse(UserId(u)), Guid.Parse(project));
        app.Llm.Reply = m => m[^1].Blocks.Any(b => b.Type == "tool_result") ? ScriptedLlm.Text("Nắng") : ScriptedLlm.Tool("get_weather", new { days = 2 });
        Assert.Equal(HttpStatusCode.OK, (await Ask(project, u.Access, "Mai có nắng không?")).Status);
        var weather = ScriptedLlm.LastResults(app.Llm.Calls.Last().Messages).Single();
        Assert.Null(weather.IsError);
        Assert.Contains("33.5", weather.Text);
        Assert.Equal((10.7769, 106.7009, 2), app.Weather.Requests.Last());
    }

    [Fact]
    public async Task DeclinedOrFailedQuestionsAreNotStored()
    {
        var (u, _, project) = await Owner();
        app.Llm.Reply = _ => new LlmReply([], "refusal", "test-model", 10, 0);
        var (s, body, _) = await Ask(project, u.Access, "…");
        Assert.Equal(HttpStatusCode.OK, s);
        Assert.True((bool)body!["refused"]!);
        Assert.Null(body["conversation_id"]);

        app.Llm.Reply = _ => throw new HttpRequestException("model down");
        (s, body, _) = await Ask(project, u.Access, "Xin chào");
        Assert.Equal(HttpStatusCode.BadGateway, s);
        Assert.Equal("CHAT_UNAVAILABLE", (string)body!["code"]!);

        // A model that never stops calling tools.
        app.Llm.Reply = _ => ScriptedLlm.Tool("get_live_status");
        Assert.Equal(HttpStatusCode.BadGateway, (await Ask(project, u.Access, "Xin chào")).Status);
        Assert.Empty((await app.Get($"/api/v1/projects/{project}/chat/conversations", u.Access)).Body!.AsArray());
    }

    [Fact]
    public async Task DailyQuotaAndDisabledAssistant()
    {
        var (u, _, project) = await Owner();
        app.Llm.Reply = _ => ScriptedLlm.Text("ok");
        for (var i = 0; i < 3; i++) Assert.Equal(HttpStatusCode.OK, (await Ask(project, u.Access, $"Câu {i}")).Status);
        var (s, body, _) = await Ask(project, u.Access, "Câu 4");
        Assert.Equal((HttpStatusCode)429, s);
        Assert.Equal("CHAT_QUOTA_EXCEEDED", (string)body!["code"]!);

        var (v, _, other) = await Owner();
        Assert.Equal(HttpStatusCode.BadRequest, (await Ask(other, v.Access, "   ")).Status);
        app.Llm.Configured = false;
        try
        {
            (s, body, _) = await Ask(other, v.Access, "Xin chào");
            Assert.Equal(HttpStatusCode.ServiceUnavailable, s);
            Assert.Equal("CHAT_DISABLED", (string)body!["code"]!);
        }
        finally { app.Llm.Configured = true; }
    }

    private static string UserId(AppFixture.User u)
    {
        var payload = u.Access.Split('.')[1];
        payload = payload.Replace('-', '+').Replace('_', '/').PadRight(payload.Length + (4 - payload.Length % 4) % 4, '=');
        return JsonDocument.Parse(Convert.FromBase64String(payload)).RootElement.GetProperty("sub").GetString()!;
    }
}
