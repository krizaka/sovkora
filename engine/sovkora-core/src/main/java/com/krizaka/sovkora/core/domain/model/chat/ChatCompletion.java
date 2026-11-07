package com.krizaka.sovkora.core.domain.model.chat;

import java.util.List;

/**
 * What a model answered, as the engines read it: the text, what the provider says it consumed and,
 * in client-executed mode, the tool calls it asked for.
 *
 * @param text the answer (or the chunk of a stream), {@code null} when the provider sent none
 * @param usage the reported usage, {@link TokenUsage#none()} when the provider reported nothing
 * @param toolCalls the calls the model asked for, empty when it asked for none
 * @param executed the calls the engine itself ran (server tools), empty when it ran none; never the
 *     calls returned to the client
 */
public record ChatCompletion(
    String text, TokenUsage usage, List<ToolCall> toolCalls, List<ToolCall> executed) {

  /** Compact canonical constructor: an absent usage is {@link TokenUsage#none()}. */
  public ChatCompletion {
    usage = usage == null ? TokenUsage.none() : usage;
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    executed = executed == null ? List.of() : List.copyOf(executed);
  }

  /** An answer whose tool calls the engine did not run. */
  public ChatCompletion(String text, TokenUsage usage, List<ToolCall> toolCalls) {
    this(text, usage, toolCalls, List.of());
  }

  /** An answer that asked for no tool. */
  public ChatCompletion(String text, TokenUsage usage) {
    this(text, usage, List.of());
  }
}
