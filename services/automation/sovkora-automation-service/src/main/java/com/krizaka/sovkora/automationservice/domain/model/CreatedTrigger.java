package com.krizaka.sovkora.automationservice.domain.model;

/**
 * A trigger just created or re-keyed, with the secret to show once.
 *
 * @param trigger the trigger
 * @param secret the webhook secret in its transport form, or null for a schedule
 */
public record CreatedTrigger(Trigger trigger, String secret) {

  /** Keeps the secret out of logs and exception messages. */
  @Override
  public String toString() {
    return "CreatedTrigger[trigger=" + trigger + ", secret=<redacted>]";
  }
}
