package com.krizaka.sovkora.studio.infrastructure.adapter.schedule;

import com.krizaka.sovkora.studio.application.service.RunInputPurgeService;
import com.krizaka.sovkora.studio.application.service.StudioRuntimeConfigService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The safety net of the input purge (krizaka-restricted#279): purges the input files of the
 * finished runs a terminal transition left unpurged, when their retention policy row says so.
 *
 * <p>The saga, a cancel, the stalled-run sweep and the result cache purge at the transition, in its
 * transaction. What reaches a terminal state elsewhere (a run {@link RunSweeper} fails past its
 * ceiling, a called run its parent cancelled) is purged here, within one sweep interval.
 */
@Component
public class RunInputPurgeSweeper {

  private static final Logger logger = LoggerFactory.getLogger(RunInputPurgeSweeper.class);

  /** The most finished runs one sweep purges; a row, like the ceilings. */
  static final String BATCH_KEY = "run.input-purge.batch-size";

  static final int BATCH_FALLBACK = 100;

  private final RunInputPurgeService inputPurge;
  private final StudioRuntimeConfigService config;

  public RunInputPurgeSweeper(RunInputPurgeService inputPurge, StudioRuntimeConfigService config) {
    this.inputPurge = Objects.requireNonNull(inputPurge, "RunInputPurgeService cannot be null");
    this.config = Objects.requireNonNull(config, "StudioRuntimeConfigService cannot be null");
  }

  /** One sweep; a failure is logged and the next sweep tries again. */
  @Scheduled(fixedDelayString = "${sovkora.studio.input-purge.interval:60000}")
  public void sweep() {
    try {
      int purged =
          inputPurge.purgeFinished(Math.max(1, config.intValue(BATCH_KEY, BATCH_FALLBACK)));
      if (purged > 0) {
        logger.info("input purge: the input files of {} finished run(s) were purged", purged);
      }
    } catch (RuntimeException failure) {
      logger.warn("input purge sweep failed; the next sweep tries again", failure);
    }
  }
}
