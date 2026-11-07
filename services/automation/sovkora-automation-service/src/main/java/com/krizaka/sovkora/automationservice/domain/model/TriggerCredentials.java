package com.krizaka.sovkora.automationservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * What the ingress needs to verify a call to a webhook trigger. The secrets are still sealed.
 *
 * @param triggerId the trigger
 * @param automationId its automation
 * @param active whether the trigger and its automation are both enabled
 * @param secretSealed the current secret, sealed
 * @param previousSealed the previous secret during a rotation grace, sealed, or null
 * @param previousUntil when the previous secret stops being accepted
 */
public record TriggerCredentials(
    UUID triggerId,
    UUID automationId,
    boolean active,
    String secretSealed,
    String previousSealed,
    Instant previousUntil) {

  /** Keeps the sealed secrets out of logs and exception messages. */
  @Override
  public String toString() {
    return "TriggerCredentials[triggerId=" + triggerId + ", secrets=<redacted>]";
  }
}
