package com.krizaka.sovkora.core.domain.model.chat;

import com.krizaka.sovkora.core.domain.model.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Internal engine-level chat request record.
 *
 * <p>This is the <b>engine-internal</b> representation of a chat request, distinct from the
 * public-facing {@link ChatRequest}. The router is responsible for mapping the public request to
 * this internal format before passing it to the engine.
 *
 * <p>Contains the raw prompt text, conversation history, AI options, and user context.
 *
 * @param prompt The current user prompt text.
 * @param messages The conversation history (defensively copied to unmodifiable list).
 * @param options the call's settings in the engine's own type (nullable — uses defaults if null).
 * @param context The execution context carrying user and session metadata.
 * @param streaming whether the answer is streamed.
 * @param clientTools the tools the client runs (ADR-093 §4), or {@code null} for the usual mode,
 *     where the engine runs its own tools. Present (even empty), the turn is in <b>client-executed
 *     mode</b>: the engine offers only these tools, the ones the actor may use, and returns the
 *     model's calls instead of running them.
 * @param featureKey the feature the turn belongs to (ADR-029), or {@code null}: it names the turn's
 *     tool-round bound.
 * @see InternalChatResponse
 */
public record InternalChatRequest(
    String prompt,
    List<ChatMessage> messages,
    EngineChatOptions options,
    Context context,
    boolean streaming,
    List<ClientTool> clientTools,
    String featureKey) {

  /** Compact constructor — defensively copies the message list and the client tools. */
  public InternalChatRequest {
    messages = messages == null ? List.of() : List.copyOf(messages);
    clientTools = clientTools == null ? null : List.copyOf(clientTools);
  }

  /** A turn with no feature key. */
  public InternalChatRequest(
      String prompt,
      List<ChatMessage> messages,
      EngineChatOptions options,
      Context context,
      boolean streaming,
      List<ClientTool> clientTools) {
    this(prompt, messages, options, context, streaming, clientTools, null);
  }

  /** Server-executed tools, the usual mode. */
  public InternalChatRequest(
      String prompt,
      List<ChatMessage> messages,
      EngineChatOptions options,
      Context context,
      boolean streaming) {
    this(prompt, messages, options, context, streaming, null);
  }

  /** Whether the client runs the tools of this turn (ADR-093 §4). */
  public boolean clientToolMode() {
    return clientTools != null;
  }

  /** The same turn in client-executed mode, offering these tools. */
  public InternalChatRequest withClientTools(List<ClientTool> tools) {
    return new InternalChatRequest(
        prompt,
        messages,
        options,
        context,
        streaming,
        tools == null ? List.of() : tools,
        featureKey);
  }

  /** The same turn, belonging to this feature (ADR-029). */
  public InternalChatRequest withFeatureKey(String key) {
    return new InternalChatRequest(prompt, messages, options, context, streaming, clientTools, key);
  }

  /** Backwards compatible constructor defaulting streaming to false. */
  public InternalChatRequest(
      String prompt, List<ChatMessage> messages, EngineChatOptions options, Context context) {
    this(prompt, messages, options, context, false);
  }

  /**
   * Compiles the message history and refined prompt into a flat list of engine messages.
   *
   * <p>Maps each {@link ChatMessage} using the provided mapper function, then appends the refined
   * prompt as a final user message.
   *
   * @param refinedPrompt The refined or original prompt text (nullable — skipped if blank).
   * @param messageMapper A function converting {@link ChatMessage} to an {@link EngineMessage}.
   * @return A mutable list of engine messages ready for prompt assembly.
   */
  public List<EngineMessage> compileMessages(
      String refinedPrompt, Function<ChatMessage, EngineMessage> messageMapper) {
    List<EngineMessage> container = new ArrayList<>();
    if (!this.messages.isEmpty()) {
      container.addAll(this.messages.stream().map(messageMapper).toList());
    }
    if (refinedPrompt != null && !refinedPrompt.isBlank()) {
      container.add(EngineMessage.user(refinedPrompt));
    }
    return new ArrayList<>(container);
  }

  /**
   * Represents an individual message in the conversation history.
   *
   * @param role The message sender's role ({@code "system"}, {@code "user"}, or {@code
   *     "assistant"}).
   * @param content The text content of the message (defaults to empty string if null).
   * @param toolCalls the calls an {@code assistant} message made (client-executed mode), else
   *     empty.
   * @param toolCallId on a {@code tool} message, the id of the call it answers, else empty. A tool
   *     message is <b>data</b>: it is never compiled as an instruction.
   */
  public record ChatMessage(
      String role, String content, List<ToolCall> toolCalls, String toolCallId) {
    /** Compact constructor — validates role and defaults null content to empty string. */
    public ChatMessage {
      if (role == null || role.isBlank()) {
        throw new IllegalArgumentException("Role cannot be empty");
      }
      if (content == null) {
        content = "";
      }
      toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
      toolCallId = toolCallId == null ? "" : toolCallId;
    }

    /** A message that carries no tool call and answers none. */
    public ChatMessage(String role, String content) {
      this(role, content, List.of(), "");
    }
  }

  /**
   * Factory method for creating a simple single-prompt request with no history or context.
   *
   * @param prompt The prompt text.
   * @return A minimal {@link InternalChatRequest} with no messages, options, or context.
   */
  public static InternalChatRequest simple(String prompt) {
    return new InternalChatRequest(prompt, List.of(), null, Context.anonymous());
  }
}
