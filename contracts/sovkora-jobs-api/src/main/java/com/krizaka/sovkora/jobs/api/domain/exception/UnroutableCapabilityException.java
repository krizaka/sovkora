package com.krizaka.sovkora.jobs.api.domain.exception;

import java.io.Serial;

/**
 * Thrown when a capability has no enabled route, at publish time and at dispatch time alike.
 *
 * <p>There is deliberately <b>no fallback routing key</b>. A default branch is what turned an
 * unknown capability into a message on the text queue and a confidently wrong answer to the user
 * (ADR-037): the failure existed either way, and the only choice was whether it was visible.
 * Catching this to route somewhere plausible re-introduces the defect it was raised to remove.
 *
 * <p>It names the capability because whoever has to act on it — an admin publishing a blueprint, an
 * operator reading a failed run — cannot find the missing row from a stack trace.
 */
public class UnroutableCapabilityException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String featureKey;

  /**
   * Constructs the refusal for a capability that resolves to no enabled route.
   *
   * @param featureKey the capability that could not be routed
   */
  public UnroutableCapabilityException(String featureKey) {
    super(
        "Capability '"
            + featureKey
            + "' has no enabled route: add a row to orazaka_capabilities with a routing_key, or"
            + " enable the existing one. There is no default queue on purpose.");
    this.featureKey = featureKey;
  }

  /**
   * Constructs the refusal with the context that makes it actionable where it was raised.
   *
   * @param featureKey the capability that could not be routed
   * @param context what was being routed — a blueprint step id, a run id
   */
  public UnroutableCapabilityException(String featureKey, String context) {
    super(
        "Capability '"
            + featureKey
            + "' has no enabled route ("
            + context
            + "): add a row to orazaka_capabilities with a routing_key, or enable the existing"
            + " one. There is no default queue on purpose.");
    this.featureKey = featureKey;
  }

  /**
   * @return the capability that could not be routed
   */
  public String featureKey() {
    return featureKey;
  }
}
