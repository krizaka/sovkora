package com.krizaka.sovkora.core.domain.model.chat;

import java.util.List;
import java.util.Objects;

/**
 * What the engine answered for one {@link AgentTurnRequest}.
 *
 * @param text the model's text (may be empty when it only calls tools)
 * @param toolCalls the calls it made, already screened against the actor's roles; never run here
 * @param usage what the turn consumed
 */
public record AgentTurnResult(String text, List<ToolCall> toolCalls, TokenUsage usage) {

  /** Compact canonical constructor. */
  public AgentTurnResult {
    text = text == null ? "" : text;
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    usage = Objects.requireNonNullElse(usage, TokenUsage.none());
  }
}
