package com.krizaka.sovkora.studio.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long a run may stay {@code RUNNING} with no step dispatched, and how many such runs one sweep
 * closes ({@code orazaka.studio-service.sweeper.dispatch}, krizaka-internal#263).
 *
 * <p>Both are bounded: a timeout under the floor would fail a run that is still being dispatched,
 * and a batch with no ceiling would hold one transaction open for as long as a backlog is long.
 *
 * @param timeout the age past which a run with no step is failed
 * @param batchSize the most runs one sweep closes; the next sweep takes the rest
 */
@org.springframework.boot.context.properties.ConfigurationProperties(
    prefix = "orazaka.studio-service.sweeper.dispatch")
public record DispatchStallProperties(
    @DefaultValue("PT5M") Duration timeout, @DefaultValue("50") int batchSize) {

  static final Duration MIN_TIMEOUT = Duration.ofSeconds(30);
  static final Duration MAX_TIMEOUT = Duration.ofHours(1);
  static final int MAX_BATCH_SIZE = 500;

  /** Compact canonical constructor enforcing the bounds (ERR-106). */
  public DispatchStallProperties {
    if (timeout.compareTo(MIN_TIMEOUT) < 0 || timeout.compareTo(MAX_TIMEOUT) > 0) {
      throw new IllegalArgumentException(
          "dispatch timeout must be between " + MIN_TIMEOUT + " and " + MAX_TIMEOUT);
    }
    if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "dispatch batch size must be between 1 and " + MAX_BATCH_SIZE);
    }
  }
}
