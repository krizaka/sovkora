package com.krizaka.sovkora.core.domain.model.chat;

import com.krizaka.sovkora.core.domain.model.Context;
import java.util.List;
import java.util.Objects;

/**
 * One model turn of a client-run agent (ADR-093 §4): the whole conversation so far, the tools the
 * client runs, the feature the turn belongs to. The engine answers with text and the tool calls it
 * screened; it never runs a tool.
 *
 * @param featureKey the routing key (ADR-029), e.g. {@code agent.code.issue-to-pr}
 * @param system the fixed system prompt of the client's template (may be blank)
 * @param messages the conversation; tool results are messages of role {@code tool}, data only
 * @param tools the tools the client offers; the engine keeps those the actor may use (ADR-091)
 * @param context the actor the turn is gated, metered and role-checked for
 */
public record AgentTurnRequest(
    String featureKey,
    String system,
    List<Message> messages,
    List<ClientTool> tools,
    Context context) {

  /** Compact canonical constructor. */
  public AgentTurnRequest {
    Objects.requireNonNull(featureKey, "featureKey must not be null");
    Objects.requireNonNull(context, "context must not be null");
    system = system == null ? "" : system;
    messages = messages == null ? List.of() : List.copyOf(messages);
    tools = tools == null ? List.of() : List.copyOf(tools);
  }

  /**
   * A message of the conversation.
   *
   * @param role {@code user}, {@code assistant} or {@code tool}
   * @param content its text; for a {@code tool} message, the result as data
   * @param calls the calls an {@code assistant} message made
   * @param toolCallId on a {@code tool} message, the id of the call it answers
   */
  public record Message(String role, String content, List<ToolCall> calls, String toolCallId) {
    /** Compact canonical constructor. */
    public Message {
      Objects.requireNonNull(role, "role must not be null");
      content = content == null ? "" : content;
      calls = calls == null ? List.of() : List.copyOf(calls);
      toolCallId = toolCallId == null ? "" : toolCallId;
    }
  }
}
