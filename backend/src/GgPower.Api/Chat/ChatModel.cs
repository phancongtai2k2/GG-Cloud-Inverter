using System.Text.Json;
using System.Text.Json.Serialization;

namespace GgPower.Api.Chat;

public sealed class ChatOptions
{
    /// <summary>Anthropic API key. Empty: the assistant is off and the chat endpoints answer 503 CHAT_DISABLED.</summary>
    public string ApiKey { get; set; } = "";
    public string Model { get; set; } = "claude-opus-5-5";
    /// <summary>low | medium | high: how much the model thinks before answering (cost and latency).</summary>
    public string Effort { get; set; } = "medium";
    public int MaxTokens { get; set; } = 16000;
    /// <summary>Questions per conversation; afterwards the app starts a new one (the history is never trimmed).</summary>
    public int MaxQuestionsPerConversation { get; set; } = 30;
    public int MaxQuestionsPerUserPerDay { get; set; } = 50;
    /// <summary>Model calls for one question (each tool round is one call).</summary>
    public int MaxModelCallsPerQuestion { get; set; } = 8;
    public string WeatherUrl { get; set; } = "https://api.open-meteo.com/v1/forecast";
}

/// <summary>
/// One block of a conversation turn, kept in <c>chat_messages.blocks</c> exactly as the model produced it, so that a
/// later question replays the history unchanged (the model rejects or ignores reasoning whose history was edited).
/// </summary>
public sealed record ChatBlock
{
    /// <summary>text | thinking | redacted_thinking | tool_use | tool_result</summary>
    public required string Type { get; init; }
    public string? Text { get; init; }
    public string? Signature { get; init; }
    public string? Data { get; init; }
    public string? Id { get; init; }
    public string? Name { get; init; }
    public JsonElement? Input { get; init; }
    public string? ToolUseId { get; init; }
    public bool? IsError { get; init; }

    public static ChatBlock OfText(string text) => new() { Type = "text", Text = text };

    public static readonly JsonSerializerOptions Json = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };
}

public sealed record ChatTurn(string Role, IReadOnlyList<ChatBlock> Blocks);

public sealed record ChatToolSpec(string Name, string Description, IReadOnlyDictionary<string, object> Properties, IReadOnlyList<string> Required);

/// <param name="StopReason">end_turn | tool_use | max_tokens | refusal | …, as the provider reports it.</param>
public sealed record LlmReply(IReadOnlyList<ChatBlock> Blocks, string StopReason, string Model, long InputTokens, long OutputTokens);

/// <summary>The language model behind the assistant; one implementation per provider.</summary>
public interface ILlmClient
{
    bool IsConfigured { get; }
    Task<LlmReply> CompleteAsync(string system, IReadOnlyList<ChatToolSpec> tools, IReadOnlyList<ChatTurn> messages, CancellationToken ct);
}
