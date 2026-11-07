package com.krizaka.sovkora.automationservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * How an automation starts. A webhook trigger never carries its secret: it is shown once, when
 * created or rotated, and only a short hint (its last characters) is kept readable.
 *
 * @param id the trigger
 * @param automationId the automation it starts
 * @param kind webhook or schedule
 * @param enabled whether it may start runs
 * @param secretHint the last characters of a webhook secret, to tell two apart; null for a schedule
 * @param schedule the slot of a schedule; null for a webhook
 * @param catchUp the missed-slot policy
 * @param nextFireAt the next slot of an enabled schedule; null otherwise
 * @param lastFiredAt when it last started a run, if ever
 * @param createdAt when it was created
 */
public record Trigger(
    UUID id,
    UUID automationId,
    TriggerKind kind,
    boolean enabled,
    String secretHint,
    ScheduleSpec schedule,
    CatchUp catchUp,
    Instant nextFireAt,
    Instant lastFiredAt,
    Instant createdAt) {

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public Trigger {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(automationId, "automationId is required");
    Objects.requireNonNull(kind, "kind is required");
    Objects.requireNonNull(catchUp, "catchUp is required");
    Objects.requireNonNull(createdAt, "createdAt is required");
  }
}
