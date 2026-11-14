package com.krizaka.sovkora.job.infrastructure.adapter.schedule;

import com.krizaka.sovkora.job.application.service.PodTokenService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Purges the revocation rows of pod tokens that expired more than a day ago, hourly. */
@Component
public class PodTokenSweeper {

  private final PodTokenService podTokenService;

  /**
   * @param podTokenService the pod tokens
   */
  public PodTokenSweeper(PodTokenService podTokenService) {
    this.podTokenService = podTokenService;
  }

  /** Keeps {@code gpu_pod_tokens} bounded. */
  @Scheduled(fixedDelayString = "${sovkora.jobs.pod-token.purge-interval-ms:3600000}")
  public void purge() {
    podTokenService.purge();
  }
}
