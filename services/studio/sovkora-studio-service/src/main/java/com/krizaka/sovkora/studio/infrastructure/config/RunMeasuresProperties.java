package com.krizaka.sovkora.studio.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * When a run still {@code RUNNING} is called stuck ({@code sovkora.studio.measures},
 * krizaka-internal#3437). A starting value, tuned from what dev shows: above the dispatch ceiling
 * (a run with no step is failed by the sweeper first) and under the run ceiling.
 *
 * @param stuckAfter the age past which an unfinished run is stuck
 */
@ConfigurationProperties(prefix = "sovkora.studio.measures")
public record RunMeasuresProperties(@DefaultValue("PT30M") Duration stuckAfter) {

  static final Duration MIN_STUCK_AFTER = Duration.ofMinutes(1);
  static final Duration MAX_STUCK_AFTER = Duration.ofHours(4);

  /** Compact canonical constructor enforcing the bounds (ERR-106). */
  public RunMeasuresProperties {
    if (stuckAfter.compareTo(MIN_STUCK_AFTER) < 0 || stuckAfter.compareTo(MAX_STUCK_AFTER) > 0) {
      throw new IllegalArgumentException(
          "stuck-after must be between " + MIN_STUCK_AFTER + " and " + MAX_STUCK_AFTER);
    }
  }
}
