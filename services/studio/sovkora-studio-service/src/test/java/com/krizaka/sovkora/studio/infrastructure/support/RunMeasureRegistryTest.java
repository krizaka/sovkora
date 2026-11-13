package com.krizaka.sovkora.studio.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.jobs.api.domain.model.FailureCause;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RunMeasureRegistry — the run measures Grafana reads")
class RunMeasureRegistryTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final RunMeasureRegistry measures = new RunMeasureRegistry(registry);

  @Test
  @DisplayName("a run that ends is counted once by outcome, and its duration is timed")
  void endedAndDuration() {
    measures.succeeded(Duration.ofSeconds(12));
    measures.failed(Duration.ofSeconds(30), FailureCause.TIMEOUT);

    assertThat(registry.get("sovkora.run.ended").tag("outcome", "succeeded").counter().count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .get("sovkora.run.ended")
                .tag("outcome", "failed")
                .tag("cause", "TIMEOUT")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .get("sovkora.run.duration")
                .tag("outcome", "failed")
                .timer()
                .totalTime(java.util.concurrent.TimeUnit.SECONDS))
        .isEqualTo(30.0);
  }

  @Test
  @DisplayName("the delay before a run's first step is dispatched is timed")
  void startDelay() {
    measures.started(Duration.ofSeconds(2));

    assertThat(registry.get("sovkora.run.start.delay").timer().count()).isEqualTo(1);
  }

  @Test
  @DisplayName("no measure carries an actor, a run, a studio or any other per-tenant label")
  void noTenantLabel() {
    measures.started(Duration.ofSeconds(1));
    measures.succeeded(Duration.ofSeconds(1));
    measures.failed(Duration.ofSeconds(1), FailureCause.GUARD_REFUSAL);

    Set<String> keys =
        registry.getMeters().stream()
            .flatMap(m -> m.getId().getTags().stream())
            .map(t -> t.getKey())
            .collect(Collectors.toSet());
    assertThat(keys).isSubsetOf("outcome", "cause");
    assertThat(registry.getMeters().stream().map(Meter::getId).map(i -> i.getName()))
        .allMatch(name -> name.startsWith("sovkora.run."));
  }
}
