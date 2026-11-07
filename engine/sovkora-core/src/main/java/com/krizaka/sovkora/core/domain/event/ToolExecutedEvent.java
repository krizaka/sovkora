package com.krizaka.sovkora.core.domain.event;

import java.util.Objects;

/**
 * Announces that the engine itself ran a tool the model asked for (a server tool; a tool the client
 * runs is returned to it, never announced here). It carries the tool's name and the call id, never
 * its arguments or result (ADR-064), so a turn's tool use can be seen from outside.
 *
 * <p>Published synchronously, in-process, on the request thread, like {@link PipelineStageEvent};
 * correlated downstream by {@code conversationId}.
 *
 * <p>It also says who asked and for which feature (the audit of a tool call needs both, #169), how
 * long the model turn had taken when the tool's run was announced, and the outcome. None of them is
 * content: no prompt, no argument, no result.
 *
 * @param conversationId the conversation the turn belongs to
 * @param toolName the tool that ran
 * @param callId the provider's id of the call, {@code null} when it sent none
 * @param userId the user the turn runs for, {@code null} when the publisher did not know
 * @param featureKey the feature of the turn (ADR-029), {@code null} when it has none
 * @param durationMs how long the turn had run when the tool was announced, in milliseconds
 * @param outcome how the tool run ended, {@link #OUTCOME_OK} for a tool that ran
 */
public record ToolExecutedEvent(
    String conversationId,
    String toolName,
    String callId,
    String userId,
    String featureKey,
    long durationMs,
    String outcome) {

  /** The outcome of a tool that ran (a refused or failed tool is not announced as executed). */
  public static final String OUTCOME_OK = "ok";

  /** An event with nothing but the tool: no user, no feature, no duration. */
  public ToolExecutedEvent(String conversationId, String toolName, String callId) {
    this(conversationId, toolName, callId, null, null, 0L, OUTCOME_OK);
  }

  /** Compact constructor enforcing non-null invariants. */
  public ToolExecutedEvent {
    Objects.requireNonNull(conversationId, "conversationId must not be null");
    Objects.requireNonNull(toolName, "toolName must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
  }
}
