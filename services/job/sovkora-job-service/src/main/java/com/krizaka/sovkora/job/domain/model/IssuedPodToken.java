package com.krizaka.sovkora.job.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A pod token as minted: the compact JWS the pod presents, and what its row records.
 *
 * @param token the compact JWS, for an {@code Authorization: Bearer} header
 * @param jti its id, the key of its revocation row
 * @param pod the pod it was minted for
 * @param expiresAt its {@code exp}
 */
public record IssuedPodToken(String token, UUID jti, PodKey pod, Instant expiresAt) {

  /** Hides the token from logs and exception messages. */
  @Override
  public String toString() {
    return "IssuedPodToken[token=<redacted>, jti="
        + jti
        + ", pod="
        + pod.value()
        + ", expiresAt="
        + expiresAt
        + "]";
  }
}
