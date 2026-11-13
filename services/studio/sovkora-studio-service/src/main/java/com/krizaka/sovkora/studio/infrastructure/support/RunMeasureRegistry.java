package com.krizaka.sovkora.studio.infrastructure.support;

import com.krizaka.sovkora.jobs.api.domain.model.FailureCause;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/**
 * The run measures Grafana reads (krizaka-internal#3437), named by function. A label is an outcome
 * or a declared cause, never an actor, a run, a studio or a key: one series per outcome, not per
 * tenant.
 *
 * <p>Prometheus names: {@code sovkora_run_start_delay_seconds}, {@code sovkora_run_ended_total},
 * {@code sovkora_run_duration_seconds}; the stuck-runs gauge {@code sovkora_run_stuck} is bound by
 * {@code RunMeasuresConfig}.
 */
public final class RunMeasureRegistry {

  /** From a run's creation to its first step handed to an executor. */
  public static final String START_DELAY = "sovkora.run.start.delay";

  /** A run that reached a terminal state, by {@code outcome} (and {@code cause} when failed). */
  public static final String ENDED = "sovkora.run.ended";

  /** From a run's creation to its end, by {@code outcome}. */
  public static final String DURATION = "sovkora.run.duration";

  /** The runs still {@code RUNNING} past the declared age. */
  public static final String STUCK = "sovkora.run.stuck";

  private static final String OUTCOME = "outcome";
  private static final String CAUSE = "cause";
  private static final String SUCCEEDED = "succeeded";
  private static final String FAILED = "failed";

  private final MeterRegistry registry;

  public RunMeasureRegistry(MeterRegistry registry) {
    this.registry = registry;
  }

  /** A run's first step was dispatched {@code delay} after the run was created. */
  public void started(Duration delay) {
    Timer.builder(START_DELAY).publishPercentileHistogram().register(registry).record(delay);
  }

  /** A run succeeded after {@code duration}. */
  public void succeeded(Duration duration) {
    registry.counter(ENDED, OUTCOME, SUCCEEDED).increment();
    duration(SUCCEEDED, duration);
  }

  /** A run failed after {@code duration}, for the declared {@code cause}. */
  public void failed(Duration duration, FailureCause cause) {
    registry.counter(ENDED, OUTCOME, FAILED, CAUSE, cause.name()).increment();
    duration(FAILED, duration);
  }

  private void duration(String outcome, Duration duration) {
    Timer.builder(DURATION)
        .tag(OUTCOME, outcome)
        .publishPercentileHistogram()
        .register(registry)
        .record(duration);
  }
}
