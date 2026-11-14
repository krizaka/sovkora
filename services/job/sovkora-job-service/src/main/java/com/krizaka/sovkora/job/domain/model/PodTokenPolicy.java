package com.krizaka.sovkora.job.domain.model;

import java.time.Duration;

/**
 * What a GPU pod's job service token is (krizaka-internal#3645): addressed to the job service, the
 * pod role and never {@code SERVICE}, and alive at most as long as the longest pod the launcher
 * starts. Its claims are written once, here, for the issuer and the verifier alike.
 *
 * @param maxLifetime the longest lifetime a token is minted with; a longer request is capped to it
 */
public record PodTokenPolicy(Duration maxLifetime) {

  /** The {@code aud} claim: the job service, and no other service. */
  public static final String AUDIENCE = "sovkora-job-service";

  /** The one authority a pod token carries ({@code roles}). */
  public static final String ROLE = "GPU_POD";

  /** The {@code kid} of the header: it routes a request to the pods' own filter chain. */
  public static final String KEY_ID = "gpu-pod-v1";

  static final Duration MIN_LIFETIME = Duration.ofMinutes(5);
  static final Duration MAX_LIFETIME = Duration.ofHours(24);

  public PodTokenPolicy {
    if (maxLifetime.compareTo(MIN_LIFETIME) < 0 || maxLifetime.compareTo(MAX_LIFETIME) > 0) {
      throw new IllegalArgumentException(
          "sovkora.jobs.pod-token.max-lifetime must be between 5 min and 24 h, not " + maxLifetime);
    }
  }

  /**
   * The lifetime a token is minted with.
   *
   * @param requested what the launcher asked for: the pod's lifetime cap
   * @return the request, capped to {@link #maxLifetime()}
   * @throws IllegalArgumentException when the request is not positive
   */
  public Duration lifetime(Duration requested) {
    if (requested.isNegative() || requested.isZero()) {
      throw new IllegalArgumentException("a pod token's lifetime must be positive");
    }
    return requested.compareTo(maxLifetime) > 0 ? maxLifetime : requested;
  }
}
