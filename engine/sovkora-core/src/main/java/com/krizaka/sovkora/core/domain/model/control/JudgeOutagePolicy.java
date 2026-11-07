package com.krizaka.sovkora.core.domain.model.control;

import java.util.Locale;

/**
 * What the control loop does when the semantic judge (Tier C / Tier D) is unavailable — a timeout,
 * an outage, or an open circuit breaker (ADR-092, sovkora-engine#125). Admin data, read from the
 * step's {@code configurationPayload} ({@value #PAYLOAD_KEY}); never a silent pass.
 */
public enum JudgeOutagePolicy {

  /** The turn stops and a person decides: « vérification indisponible ». The default. */
  ESCALATE,

  /**
   * The answer is served on the deterministic tiers (A, B) alone, with a visible badge, and the
   * degraded run is audited through the outbox.
   */
  DETERMINISTIC_ONLY;

  /** The key of the policy in a validation step's configuration payload. */
  public static final String PAYLOAD_KEY = "onJudgeOutage";

  /**
   * Reads a policy from admin data.
   *
   * @param value the stored value (any case), or {@code null}
   * @param fallback the policy when the value is absent or unknown — an unknown value never relaxes
   *     the control
   * @return the policy
   */
  public static JudgeOutagePolicy parse(Object value, JudgeOutagePolicy fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      return ESCALATE;
    }
  }
}
