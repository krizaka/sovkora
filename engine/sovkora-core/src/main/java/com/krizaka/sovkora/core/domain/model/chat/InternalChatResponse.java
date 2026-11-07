package com.krizaka.sovkora.core.domain.model.chat;

import java.util.List;
import java.util.Map;

/**
 * Internal engine-level chat response record.
 *
 * <p>Wraps the generated text content, conversation thread ID, what the turn consumed, and provider
 * metadata. Used both for synchronous single responses and for individual streaming chunks.
 *
 * <p>Token usage is a <b>component</b>, not a metadata entry: it is what chat is billed on (ADR-033
 * §7), and a number that decides what a user is charged must not be reachable only by string key.
 * {@code metadata} keeps what is genuinely provider-specific and free-form.
 *
 * @param content The generated text content (defaults to empty string if null).
 * @param conversationId The session identifier for conversation tracking (nullable).
 * @param tokenUsage What this turn consumed; {@link TokenUsage#none()} on a streaming chunk, where
 *     the provider only reports usage once at the end.
 * @param metadata Provider-specific metadata (e.g. provider key, duration).
 * @param toolCalls the calls the model asked for in client-executed mode (ADR-093 §4), already
 *     screened against the tools offered; empty otherwise.
 * @see InternalChatRequest
 */
public record InternalChatResponse(
    String content,
    String conversationId,
    TokenUsage tokenUsage,
    Map<String, Object> metadata,
    List<ToolCall> toolCalls) {

  /** An answer that asked for no tool. */
  public InternalChatResponse(
      String content, String conversationId, TokenUsage tokenUsage, Map<String, Object> metadata) {
    this(content, conversationId, tokenUsage, metadata, List.of());
  }

  /** Compact constructor — defaults null content to empty string and copies metadata. */
  public InternalChatResponse {
    if (content == null) {
      content = "";
    }
    if (tokenUsage == null) {
      tokenUsage = TokenUsage.none();
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  /**
   * Overload for the many call sites that carry no usage — streaming chunks, tests, and every
   * non-inference path that still answers with this record.
   *
   * @param content The generated text content.
   * @param conversationId The session identifier.
   * @param metadata Provider-specific metadata.
   */
  public InternalChatResponse(String content, String conversationId, Map<String, Object> metadata) {
    this(content, conversationId, TokenUsage.none(), metadata);
  }
}
