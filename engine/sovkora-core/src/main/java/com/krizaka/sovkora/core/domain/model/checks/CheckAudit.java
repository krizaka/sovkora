package com.krizaka.sovkora.core.domain.model.checks;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of the external-check audit, written for every call, refused or not. It never holds a
 * name, a birth date, a court record or a credit figure: the subject is a keyed HMAC of its id.
 *
 * @param at when
 * @param actorId the actor, or {@code null}
 * @param runId the run, or {@code null}
 * @param provider the connector's provider name
 * @param purpose what the check was for
 * @param keySource whose key was used, or {@code null} when refused before the key
 * @param outcome what it answered
 * @param refusal why it was refused, or {@code null}
 * @param subjectRef the HMAC-SHA-256 (hex) of the subject id, keyed by the sealed pepper
 * @param durationMs the time spent, or {@code null} when no provider call was made
 * @param providerRef the provider's opaque reference, or {@code null}
 * @param providerCostMicros the provider's own price (admin row) when the platform key paid for the
 *     call, in millionths of a Canadian dollar; {@code null} otherwise
 */
public record CheckAudit(
    Instant at,
    String actorId,
    UUID runId,
    String provider,
    CheckPurpose purpose,
    KeySource keySource,
    CheckOutcome outcome,
    CheckRefusal refusal,
    String subjectRef,
    Long durationMs,
    String providerRef,
    Long providerCostMicros) {

  /**
   * A line with no provider cost.
   *
   * @param at when
   * @param actorId the actor
   * @param runId the run
   * @param provider the provider
   * @param purpose the purpose
   * @param keySource whose key
   * @param outcome the outcome
   * @param refusal why refused
   * @param subjectRef the HMAC of the subject id
   * @param durationMs the time spent
   * @param providerRef the provider's reference
   */
  public CheckAudit(
      Instant at,
      String actorId,
      UUID runId,
      String provider,
      CheckPurpose purpose,
      KeySource keySource,
      CheckOutcome outcome,
      CheckRefusal refusal,
      String subjectRef,
      Long durationMs,
      String providerRef) {
    this(
        at,
        actorId,
        runId,
        provider,
        purpose,
        keySource,
        outcome,
        refusal,
        subjectRef,
        durationMs,
        providerRef,
        null);
  }

  /** Validates the line. */
  public CheckAudit {
    Objects.requireNonNull(at, "at must not be null");
    Objects.requireNonNull(provider, "provider must not be null");
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(subjectRef, "subjectRef must not be null");
  }
}
