package com.krizaka.sovkora.job.infrastructure.adapter.rest.dto;

import java.util.List;

/**
 * The pods that still run, read by the launcher from RunPod: every other pod's token is revoked.
 *
 * @param running the keys of the running pods (an empty list revokes every live token)
 */
public record PodSweepRequest(List<String> running) {

  static final int MAX_RUNNING = 64;

  public PodSweepRequest {
    if (running == null || running.size() > MAX_RUNNING) {
      throw new IllegalArgumentException("running is a list of at most 64 pod keys");
    }
    running = List.copyOf(running);
  }
}
