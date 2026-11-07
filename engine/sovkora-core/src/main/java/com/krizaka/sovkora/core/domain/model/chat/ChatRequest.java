package com.krizaka.sovkora.core.domain.model.chat;

import com.krizaka.sovkora.core.domain.model.AiRequest;
import com.krizaka.sovkora.core.domain.model.Context;
import java.util.List;
import java.util.Map;

/**
 * Unified chat request record for all AI interactions.
 *
 * <p>Supports both single-turn prompt and multi-turn message history.
 *
 * @param prompt The current user input.
 * @param messages The conversation history as a list of {@link ChatMessage}.
 * @param settings AI-specific configuration settings.
 * @param context The execution context carrying session and user preferences.
 */
public record ChatRequest(
    String prompt, List<ChatMessage> messages, Map<String, Object> settings, Context context)
    implements AiRequest {
  public ChatRequest {
    AiRequest.requireValid(prompt, context);
    messages = messages == null ? List.of() : List.copyOf(messages);
    settings = settings != null ? Map.copyOf(settings) : Map.of();
  }

  /**
   * Represents an individual message in a chat history.
   *
   * @param role The role of the sender (e.g., "system", "user", "assistant").
   * @param content The text content of the message.
   */
  public record ChatMessage(String role, String content) {}

  /**
   * Utility factory for creating a simple chat request without history or options.
   *
   * @param prompt The user input.
   * @return A configured {@link ChatRequest}.
   */
  public static ChatRequest simple(String prompt) {
    return new ChatRequest(prompt, List.of(), null, Context.anonymous());
  }
}
