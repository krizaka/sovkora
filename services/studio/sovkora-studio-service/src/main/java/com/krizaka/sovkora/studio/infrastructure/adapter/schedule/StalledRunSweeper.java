package com.krizaka.sovkora.studio.infrastructure.adapter.schedule;

import com.krizaka.sovkora.studio.application.service.StalledRunService;
import com.krizaka.sovkora.studio.infrastructure.config.DispatchStallProperties;
import java.time.Clock;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Schedules {@link StalledRunService}: a run with no dispatched step ends instead of hanging. */
@Component
public class StalledRunSweeper {

  private final StalledRunService stalledRuns;
  private final DispatchStallProperties properties;
  private final Clock clock;

  public StalledRunSweeper(
      StalledRunService stalledRuns, DispatchStallProperties properties, Clock clock) {
    this.stalledRuns = Objects.requireNonNull(stalledRuns, "StalledRunService required");
    this.properties = Objects.requireNonNull(properties, "properties required");
    this.clock = Objects.requireNonNull(clock, "Clock required");
  }

  /** Fails, at most one batch of, the runs older than the timeout that dispatched nothing. */
  @Scheduled(fixedDelayString = "${orazaka.studio-service.sweeper.interval:60000}")
  public void sweep() {
    stalledRuns.failUndispatched(
        clock.instant().minus(properties.timeout()), properties.batchSize());
  }
}
