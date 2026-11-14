package com.krizaka.sovkora.job.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.job.domain.model.IssuedPodToken;
import java.time.Instant;

/**
 * A minted pod token, for the launcher to put in the pod's environment and nowhere else.
 *
 * @param token the compact JWS
 * @param jti its id
 * @param expiresAt its {@code exp}
 */
public record PodTokenResponse(String token, String jti, Instant expiresAt) {

  /**
   * @param issued the minted token
   * @return its wire form
   */
  public static PodTokenResponse of(IssuedPodToken issued) {
    return new PodTokenResponse(issued.token(), issued.jti().toString(), issued.expiresAt());
  }

  /** Hides the token from logs and exception messages. */
  @Override
  public String toString() {
    return "PodTokenResponse[token=<redacted>, jti=" + jti + ", expiresAt=" + expiresAt + "]";
  }
}
