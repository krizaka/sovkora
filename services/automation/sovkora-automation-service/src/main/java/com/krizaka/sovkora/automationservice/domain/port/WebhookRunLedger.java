package com.krizaka.sovkora.automationservice.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The ledger of webhook deliveries: every {@code webhook-id} accepted for a trigger, kept for the
 * replay window. Its primary key is the replay guard (ADR-084 §2).
 */
public interface WebhookRunLedger {

  /**
   * The run a webhook delivery already started.
   *
   * @param triggerId the trigger
   * @param webhookId the {@code webhook-id}
   * @return the run, empty when the id was never used
   */
  Optional<UUID> existingWebhookRun(UUID triggerId, String webhookId);

  /**
   * Claims a {@code webhook-id} and records the run it starts, atomically: one of two parallel
   * deliveries of the same id wins, the other records nothing.
   *
   * @param runId the new run's id
   * @param automationId the automation
   * @param triggerId the trigger
   * @param webhookId the {@code webhook-id}
   * @param payload the body as JSON text when it is a JSON object within the kept size, else null
   * @param payloadBytes the size of the body
   * @return true when this call claimed the id and recorded the run
   */
  boolean recordWebhookRun(
      UUID runId,
      UUID automationId,
      UUID triggerId,
      String webhookId,
      String payload,
      int payloadBytes);

  /**
   * Forgets the webhook ids older than the cut-off.
   *
   * @param before the cut-off
   * @return how many were forgotten
   */
  int purgeDeliveries(Instant before);
}
