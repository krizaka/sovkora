package com.krizaka.sovkora.automationservice.domain.model;

/**
 * Why an inbound call was refused. The adapter turns it into a status; the domain does not know
 * HTTP. Every cause of an unauthenticated call is the same value on purpose: a caller must not be
 * able to tell a wrong secret from an unknown trigger or a stale timestamp.
 */
public enum Rejection {
  /** The body is larger than the rule set allows. */
  PAYLOAD_TOO_LARGE("payload_too_large"),
  /** Too many calls for this trigger. */
  RATE_LIMITED("rate_limited"),
  /** The trigger's daily budget of runs is spent. */
  DAILY_BUDGET("daily_budget_reached"),
  /**
   * Missing or malformed headers, stale timestamp, unknown or disabled trigger, wrong signature.
   */
  UNAUTHENTICATED("invalid_webhook_request"),
  /** A verified call that reuses a {@code webhook-id}. */
  REPLAYED("webhook_replayed");

  private final String code;

  Rejection(String code) {
    this.code = code;
  }

  /**
   * The stable code of the problem.
   *
   * @return the code
   */
  public String code() {
    return code;
  }
}
