package com.krizaka.sovkora.core.domain.model.control;

/**
 * The typed outcome of one control pass over an output (ADR-092 §3, sovkora-engine#114).
 *
 * <p>An output may be served only under a {@link Validated} verdict, or a {@link Degraded} one (the
 * judge being unavailable and the policy allowing the deterministic tiers alone, with a badge): a
 * {@link DriftAdjustment} asks for another attempt with the diff injected, a {@link
 * HumanEscalation} stops the turn and hands it to a person. Pure Java 21, no framework type.
 */
public sealed interface ControlVerdict
    permits Validated, Degraded, DriftAdjustment, HumanEscalation {

  /**
   * @return why the output drifted, or a statement that every check passed
   */
  String reason();

  /**
   * @return the action taken, as persisted in the audit record ({@code VALIDATED}, {@code
   *     ADJUSTED}, {@code ESCALATED}, {@code DEGRADED})
   */
  String actionTaken();

  /**
   * @return whether the output this verdict is about may be served
   */
  default boolean servable() {
    return this instanceof Validated || this instanceof Degraded;
  }
}
