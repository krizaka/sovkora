package com.krizaka.sovkora.automationservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One run of an automation, as the history shows it.
 *
 * @param id the run
 * @param automationId the automation
 * @param automationName the automation's label when it was read
 * @param triggerId the trigger that started it, null when that trigger was removed
 * @param triggerKind webhook or schedule
 * @param status where it is
 * @param studioRunId the Studio run it started, once accepted
 * @param errorCode a stable code when it failed
 * @param errorMessage the engine's refusal in a few words
 * @param webhookId the {@code webhook-id} of the delivery that started it, if a webhook did
 * @param payloadBytes the size of the webhook body it was started with
 * @param startedAt when it was recorded
 * @param finishedAt when it ended
 */
public record AutomationRun(
    UUID id,
    UUID automationId,
    String automationName,
    UUID triggerId,
    TriggerKind triggerKind,
    RunStatus status,
    UUID studioRunId,
    String errorCode,
    String errorMessage,
    String webhookId,
    Integer payloadBytes,
    Instant startedAt,
    Instant finishedAt) {

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public AutomationRun {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(automationId, "automationId is required");
    Objects.requireNonNull(status, "status is required");
    Objects.requireNonNull(startedAt, "startedAt is required");
  }
}
