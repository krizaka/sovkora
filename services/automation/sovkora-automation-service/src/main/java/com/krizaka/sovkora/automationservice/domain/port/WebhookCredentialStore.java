package com.krizaka.sovkora.automationservice.domain.port;

import com.krizaka.sovkora.automationservice.domain.model.TriggerCredentials;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * What the webhook ingress reads about a trigger before it knows who is calling: it carries no
 * owner, because the signature is what proves the caller (ADR-084 §2).
 */
public interface WebhookCredentialStore {

  /**
   * What the ingress needs to verify a call to a trigger.
   *
   * @param triggerId the trigger
   * @return its credentials, empty when it is not a webhook trigger
   */
  Optional<TriggerCredentials> credentials(UUID triggerId);

  /**
   * Forgets the previous secrets whose grace is over.
   *
   * @param now the instant
   * @return how many were cleared
   */
  int clearExpiredPreviousSecrets(Instant now);
}
