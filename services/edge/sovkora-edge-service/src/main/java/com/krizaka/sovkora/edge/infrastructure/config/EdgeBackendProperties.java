package com.krizaka.sovkora.edge.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the edge treats the backends it proxies to ({@code sovkora.edge.backend}, krizaka-internal
 * #2132): how long it waits for them, and when it stops calling one that is down or slow. Every
 * value has bounds checked at startup, so a typo in an environment variable fails the deployment
 * instead of disarming the breaker.
 *
 * @param timeouts how long one call may take to connect and to answer
 * @param breaker when a backend's circuit opens, and for how long
 * @param retryAfter the {@code Retry-After} of a timeout or a refused connection while the circuit
 *     is still closed (an open circuit says its own remaining time); 1 s to 1 h
 */
@ConfigurationProperties(prefix = "sovkora.edge.backend")
public record EdgeBackendProperties(Timeouts timeouts, Breaker breaker, Duration retryAfter) {

  public EdgeBackendProperties {
    if (timeouts == null || breaker == null) {
      throw new IllegalArgumentException(
          "sovkora.edge.backend.timeouts and sovkora.edge.backend.breaker are required");
    }
    requireBetween("retry-after", retryAfter, Duration.ofSeconds(1), Duration.ofHours(1));
  }

  /**
   * The timeouts of one backend call.
   *
   * @param connect the TCP connect; 100 ms to 30 s
   * @param response the wait for the response headers; 100 ms to 10 min. It never bounds a body
   *     already streaming, so an open SSE stream outlives it.
   */
  public record Timeouts(Duration connect, Duration response) {

    public Timeouts {
      requireBetween("timeouts.connect", connect, Duration.ofMillis(100), Duration.ofSeconds(30));
      requireBetween("timeouts.response", response, Duration.ofMillis(100), Duration.ofMinutes(10));
    }
  }

  /**
   * One circuit breaker per backend, over a window of its last calls.
   *
   * @param failureRatePercent the share of failed calls in the window that opens it; 1 to 100
   * @param slowCallThreshold a call whose headers take longer is slow; 100 ms to 10 min
   * @param slowCallRatePercent the share of slow calls in the window that opens it; 1 to 100
   * @param windowCalls the number of last calls the rates are measured on; 2 to 1000
   * @param minimumCalls the calls needed before a rate is judged; 1 to {@code windowCalls}
   * @param openDuration how long an open circuit refuses before it lets a probe through; 1 s to 10
   *     min
   * @param halfOpenCalls the probes that decide whether it closes again; 1 to 50
   */
  public record Breaker(
      int failureRatePercent,
      Duration slowCallThreshold,
      int slowCallRatePercent,
      int windowCalls,
      int minimumCalls,
      Duration openDuration,
      int halfOpenCalls) {

    public Breaker {
      requireBetween("breaker.failure-rate-percent", failureRatePercent, 1, 100);
      requireBetween(
          "breaker.slow-call-threshold",
          slowCallThreshold,
          Duration.ofMillis(100),
          Duration.ofMinutes(10));
      requireBetween("breaker.slow-call-rate-percent", slowCallRatePercent, 1, 100);
      requireBetween("breaker.window-calls", windowCalls, 2, 1000);
      requireBetween("breaker.minimum-calls", minimumCalls, 1, windowCalls);
      requireBetween(
          "breaker.open-duration", openDuration, Duration.ofSeconds(1), Duration.ofMinutes(10));
      requireBetween("breaker.half-open-calls", halfOpenCalls, 1, 50);
    }
  }

  private static void requireBetween(String name, Duration value, Duration min, Duration max) {
    if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
      throw new IllegalArgumentException(
          "sovkora.edge.backend." + name + " must be between " + min + " and " + max);
    }
  }

  private static void requireBetween(String name, int value, int min, int max) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(
          "sovkora.edge.backend." + name + " must be between " + min + " and " + max);
    }
  }
}
