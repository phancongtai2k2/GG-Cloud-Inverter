using System.Text.Json;
using Anthropic;
using Anthropic.Models.Beta.Messages;
using Microsoft.Extensions.Options;

namespace GgPower.Api.Chat;

/// <summary>Claude through the official Anthropic SDK (Messages API, beta endpoint for server-side fallbacks).</summary>
public sealed class ClaudeLlmClient(IOptions<ChatOptions> options) : ILlmClient
{
    private readonly ChatOptions _o = options.Value;
    private AnthropicClient? _client;

    public bool IsConfigured => _o.ApiKey.Length > 0;

    private AnthropicClient Client => _client ??= new AnthropicClient { ApiKey = _o.ApiKey };

    public async Task<LlmReply> CompleteAsync(string system, IReadOnlyList<ChatToolSpec> tools, IReadOnlyList<ChatTurn> messages, CancellationToken ct)
    {
        var request = new MessageCreateParams
        {
            Model = _o.Model,
            MaxTokens = _o.MaxTokens,
            // A request the model declines on safety grounds is answered by a fallback model chosen by the API.
            Betas = ["server-side-fallback-2026-07-01"],
            Fallbacks = new Default(),
            // System prompt and tools never change between requests, so they stay cached.
            System = new List<BetaTextBlockParam> { new() { Text = system, CacheControl = new BetaCacheControlEphemeral() } },
            Thinking = new BetaThinkingConfigAdaptive(),
            OutputConfig = new BetaOutputConfig
            {
                Effort = _o.Effort switch { "low" => Effort.Low, "high" => Effort.High, _ => Effort.Medium },
            },
            Tools = tools.Select(t => (BetaToolUnion)new BetaTool
            {
                Name = t.Name,
                Description = t.Description,
                InputSchema = new()
                {
                    Properties = t.Properties.ToDictionary(p => p.Key, p => JsonSerializer.SerializeToElement(p.Value)),
                    Required = [.. t.Required],
                },
            }).ToList(),
            Messages = messages.Select(ToParam).ToList(),
        };
        var r = await Client.Beta.Messages.Create(request, ct);
        return new LlmReply(FromResponse(r.Content), r.StopReason?.Raw() ?? "", r.Model.Raw(), r.Usage.InputTokens, r.Usage.OutputTokens);
    }

    private static BetaMessageParam ToParam(ChatTurn turn) => new()
    {
        Role = turn.Role == "assistant" ? Role.Assistant : Role.User,
        Content = turn.Blocks.Select(b => (BetaContentBlockParam)(b.Type switch
        {
            "text" => new BetaTextBlockParam { Text = b.Text! },
            "thinking" => new BetaThinkingBlockParam { Thinking = b.Text ?? "", Signature = b.Signature! },
            "redacted_thinking" => new BetaRedactedThinkingBlockParam { Data = b.Data! },
            "tool_use" => new BetaToolUseBlockParam
            {
                ID = b.Id!, Name = b.Name!,
                Input = b.Input is { ValueKind: JsonValueKind.Object } input
                    ? input.EnumerateObject().ToDictionary(p => p.Name, p => p.Value.Clone())
                    : new Dictionary<string, JsonElement>(),
            },
            "tool_result" => new BetaToolResultBlockParam { ToolUseID = b.ToolUseId!, Content = b.Text ?? "", IsError = b.IsError ?? false },
            var other => throw new InvalidOperationException($"unknown chat block type {other}"),
        })).ToList(),
    };

    private static List<ChatBlock> FromResponse(IReadOnlyList<BetaContentBlock> content)
    {
        // After a fallback in the middle of the answer, the declined part's reasoning and tool calls are not passed back
        // (only its text); the fallback block itself is an audit marker.
        var lastFallback = -1;
        for (var i = 0; i < content.Count; i++)
            if (content[i].TryPickFallback(out _)) lastFallback = i;
        var blocks = new List<ChatBlock>();
        for (var i = 0; i < content.Count; i++)
        {
            var b = content[i];
            var beforeFallback = i < lastFallback;
            if (b.TryPickText(out var text)) blocks.Add(ChatBlock.OfText(text.Text));
            else if (beforeFallback) continue;
            else if (b.TryPickThinking(out var thinking)) blocks.Add(new ChatBlock { Type = "thinking", Text = thinking.Thinking, Signature = thinking.Signature });
            else if (b.TryPickRedactedThinking(out var redacted)) blocks.Add(new ChatBlock { Type = "redacted_thinking", Data = redacted.Data });
            else if (b.TryPickToolUse(out var use))
                blocks.Add(new ChatBlock { Type = "tool_use", Id = use.ID, Name = use.Name, Input = JsonSerializer.SerializeToElement(use.Input) });
        }
        return blocks;
    }
}
