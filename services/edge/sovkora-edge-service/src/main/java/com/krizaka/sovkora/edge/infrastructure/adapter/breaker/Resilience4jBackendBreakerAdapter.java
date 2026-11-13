package com.krizaka.sovkora.edge.infrastructure.adapter.breaker;

import com.krizaka.sovkora.edge.infrastructure.config.EdgeBackendProperties;
import com.krizaka.sovkora.edge.infrastructure.config.EdgeRoutesProperties;
import com.krizaka.sovkora.edge.infrastructure.support.BackendAdmission;
import com.krizaka.sovkora.edge.infrastructure.support.BackendBreaker;
import com.krizaka.sovkora.edge.infrastructure.support.BackendCall;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * {@link BackendBreaker} over Resilience4j: one circuit per backend of the route table. Routes that
 * share a backend share its circuit, since a backend that is down is down for all its prefixes. A
 * call fails when the backend cannot be reached, does not answer in time, or answers 502, 503 or
 * 504 (it, or what it depends on, is down); it is slow when its headers take longer than the
 * slow-call threshold.
 *
 * <p>Meters: {@code edge.backend.breaker.state} (1 for the circuit's current state, 0 for the
 * others, by {@code backend} and {@code state}) and {@code edge.backend.rejections} (calls refused
 * by an open circuit, by {@code backend}).
 */
@Component
final class Resilience4jBackendBreakerAdapter implements BackendBreaker {

  private static final Set<Integer> GATEWAY_FAILURES = Set.of(502, 503, 504);
  private static final List<CircuitBreaker.State> GAUGED_STATES =
      List.of(
          CircuitBreaker.State.CLOSED, CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN);

  private final Map<String, Circuit> circuits;
  private final Duration openDuration;
  private final Duration defaultRetryAfter;
  private final Clock clock;

  Resilience4jBackendBreakerAdapter(
      EdgeBackendProperties properties,
      EdgeRoutesProperties routes,
      MeterRegistry meters,
      Clock clock) {
    this.openDuration = properties.breaker().openDuration();
    this.defaultRetryAfter = properties.retryAfter();
    this.clock = clock;
    CircuitBreakerConfig config = circuitConfig(properties.breaker(), clock);
    Map<String, Circuit> byBackend = new HashMap<>();
    for (EdgeRoutesProperties.Route route : routes.routes()) {
      byBackend.computeIfAbsent(
          backendKey(route.target()), key -> circuit(route.target(), config, meters));
    }
    this.circuits = Map.copyOf(byBackend);
  }

  @Override
  public BackendAdmission admit(URI backend) {
    Circuit circuit = circuitOf(backend);
    if (circuit.breaker().tryAcquirePermission()) {
      return new BackendAdmission.Granted(new Call(circuit.breaker()));
    }
    circuit.rejections().increment();
    return new BackendAdmission.Refused(retryAfter(circuit));
  }

  @Override
  public Duration retryAfter(URI backend) {
    return retryAfter(circuitOf(backend));
  }

  private Duration retryAfter(Circuit circuit) {
    Instant openedAt = circuit.openedAt().get();
    if (circuit.breaker().getState() != CircuitBreaker.State.OPEN || openedAt == null) {
      return defaultRetryAfter;
    }
    Duration left = openDuration.minus(Duration.between(openedAt, clock.instant()));
    return left.isNegative() ? Duration.ZERO : left;
  }

  private Circuit circuitOf(URI backend) {
    Circuit circuit = circuits.get(backendKey(backend));
    if (circuit == null) {
      throw new IllegalArgumentException("Not a backend of the route table: " + backend);
    }
    return circuit;
  }

  private Circuit circuit(URI backend, CircuitBreakerConfig config, MeterRegistry meters) {
    String name = backend.getRawAuthority();
    CircuitBreaker breaker = CircuitBreaker.of(name, config);
    AtomicReference<Instant> openedAt = new AtomicReference<>();
    breaker
        .getEventPublisher()
        .onStateTransition(
            event -> {
              if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                openedAt.set(clock.instant());
              }
            });
    for (CircuitBreaker.State state : GAUGED_STATES) {
      Gauge.builder("edge.backend.breaker.state", breaker, cb -> cb.getState() == state ? 1 : 0)
          .description("1 for the current state of the backend's circuit, 0 for the others")
          .tags("backend", name, "state", state.name().toLowerCase(Locale.ROOT))
          .register(meters);
    }
    Counter rejections =
        Counter.builder("edge.backend.rejections")
            .description("Calls refused at once because the backend's circuit is open")
            .tag("backend", name)
            .register(meters);
    return new Circuit(breaker, openedAt, rejections);
  }

  private static CircuitBreakerConfig circuitConfig(
      EdgeBackendProperties.Breaker settings, Clock clock) {
    return CircuitBreakerConfig.custom()
        .failureRateThreshold(settings.failureRatePercent())
        .slowCallDurationThreshold(settings.slowCallThreshold())
        .slowCallRateThreshold(settings.slowCallRatePercent())
        .slidingWindow(
            settings.windowCalls(), settings.minimumCalls(), SlidingWindowType.COUNT_BASED)
        .waitDurationInOpenState(settings.openDuration())
        .permittedNumberOfCallsInHalfOpenState(settings.halfOpenCalls())
        .recordResult(
            result -> result instanceof Integer status && GATEWAY_FAILURES.contains(status))
        .clock(clock)
        .build();
  }

  private static String backendKey(URI backend) {
    return backend.getScheme() + "://" + backend.getRawAuthority();
  }

  /** A backend's circuit, when it last opened, and its rejection counter. */
  private record Circuit(
      CircuitBreaker breaker, AtomicReference<Instant> openedAt, Counter rejections) {}

  /** One admitted call: the first outcome it reports is the one recorded. */
  private static final class Call implements BackendCall {

    private final CircuitBreaker breaker;
    private final long startedNanos = System.nanoTime();
    private final AtomicBoolean reported = new AtomicBoolean();

    Call(CircuitBreaker breaker) {
      this.breaker = breaker;
    }

    @Override
    public void answered(int status) {
      if (reported.compareAndSet(false, true)) {
        breaker.onResult(elapsedNanos(), TimeUnit.NANOSECONDS, status);
      }
    }

    @Override
    public void failed(Throwable cause) {
      if (reported.compareAndSet(false, true)) {
        breaker.onError(elapsedNanos(), TimeUnit.NANOSECONDS, cause);
      }
    }

    @Override
    public void released() {
      if (reported.compareAndSet(false, true)) {
        breaker.releasePermission();
      }
    }

    private long elapsedNanos() {
      return System.nanoTime() - startedNanos;
    }
  }
}
