package com.krizaka.sovkora.core.domain.model.control;

import java.time.Instant;
import java.util.Objects;

/**
 * What the audit keeps of a verdict (Loi 25 art. 12.1, ADR-092 §4 rule 7).
 *
 * @param runId the run (or conversation) the verdict is about
 * @param driftReason why the output drifted
 * @param humanActorId the person who decided, {@code null} while no person has
 * @param timestamp when the verdict was reached
 * @param actionTaken {@code VALIDATED}, {@code ADJUSTED}, {@code ESCALATED} or {@code DEGRADED}
 */
public record ControlVerdictRecord(
    String runId, String driftReason, String humanActorId, Instant timestamp, String actionTaken) {

  /** Validates the required fields. */
  public ControlVerdictRecord {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(driftReason, "driftReason");
    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(actionTaken, "actionTaken");
  }

  /**
   * Builds the record of a verdict reached by the engine alone.
   *
   * @param runId the run the verdict is about
   * @param verdict the verdict
   * @param timestamp when it was reached
   * @return the record, with no human actor
   */
  public static ControlVerdictRecord of(String runId, ControlVerdict verdict, Instant timestamp) {
    return new ControlVerdictRecord(
        runId, verdict.reason(), null, timestamp, verdict.actionTaken());
  }
}
