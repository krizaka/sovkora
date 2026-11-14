package com.krizaka.sovkora.job.infrastructure.adapter.rest.dto;

/**
 * What the launcher asks for when it starts a pod.
 *
 * @param lifetimeSeconds the pod's lifetime cap; the job service caps it to its own maximum
 */
public record PodTokenRequest(long lifetimeSeconds) {

  public PodTokenRequest {
    if (lifetimeSeconds <= 0) {
      throw new IllegalArgumentException("lifetimeSeconds must be positive");
    }
  }
}
