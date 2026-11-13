package com.krizaka.sovkora.edge.infrastructure.adapter.breaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.krizaka.sovkora.edge.infrastructure.config.EdgeBackendProperties;
import com.krizaka.sovkora.edge.infrastructure.config.EdgeBackendProperties.Breaker;
import com.krizaka.sovkora.edge.infrastructure.config.EdgeBackendProperties.Timeouts;
import com.krizaka.sovkora.edge.infrastructure.config.EdgeRoutesProperties;
import com.krizaka.sovkora.edge.infrastructure.config.EdgeRoutesProperties.Route;
import com.krizaka.sovkora.edge.infrastructure.support.BackendAdmission;
import com.krizaka.sovkora.edge.infrastructure.support.BackendCall;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Resilience4jBackendBreakerAdapterTest {

  private static final URI CORE = URI.create("http://core:8080");
  private static final URI IDENTITY = URI.create("http://identity:8083");
  private static final Duration OPEN = Duration.ofSeconds(30);

  private final MovableClock clock = new MovableClock();
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final Resilience4jBackendBreakerAdapter breaker =
      new Resilience4jBackendBreakerAdapter(
          new EdgeBackendProperties(
              new Timeouts(Duration.ofSeconds(2), Duration.ofSeconds(60)),
              new Breaker(50, Duration.ofSeconds(30), 100, 4, 3, OPEN, 1),
              Duration.ofSeconds(5)),
          new EdgeRoutesProperties(
              List.of(
                  new Route("/", CORE),
                  new Route("/api/v1/auth", IDENTITY),
                  new Route("/api/v1/profile", URI.create("http://identity:8083/")))),
          meters,
          clock);

  @Test
  @DisplayName("Closed, the breaker admits every call and advises the default Retry-After")
  void closedAdmits() {
    assertThat(breaker.admit(CORE)).isInstanceOf(BackendAdmission.Granted.class);
    assertThat(breaker.retryAfter(CORE)).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("After the failures it refuses, with the time left before it lets a probe through")
  void openRefusesWithTheTimeLeft() {
    failThreeTimes(CORE);

    assertThat(breaker.admit(CORE)).isEqualTo(new BackendAdmission.Refused(OPEN));
    clock.advance(Duration.ofSeconds(12));
    assertThat(breaker.admit(CORE)).isEqualTo(new BackendAdmission.Refused(Duration.ofSeconds(18)));
    assertThat(breaker.retryAfter(CORE)).isEqualTo(Duration.ofSeconds(18));
    assertThat(meters.get("edge.backend.rejections").tag("backend", "core:8080").counter().count())
        .isEqualTo(2.0);
  }

  @Test
  @DisplayName("A backend's 502, 503 or 504 is a failure; any other answer is a success")
  void gatewayStatusesAreFailures() {
    answer(CORE, 404);
    answer(CORE, 500);
    answer(CORE, 200);
    assertThat(breaker.admit(CORE)).isInstanceOf(BackendAdmission.Granted.class);

    answer(IDENTITY, 502);
    answer(IDENTITY, 503);
    answer(IDENTITY, 504);
    assertThat(breaker.admit(IDENTITY)).isInstanceOf(BackendAdmission.Refused.class);
  }

  @Test
  @DisplayName("After the open duration one probe goes through; its success closes the breaker")
  void halfOpenProbeCloses() {
    failThreeTimes(CORE);
    clock.advance(OPEN.plusMillis(1));

    BackendCall probe = granted(CORE);
    assertThat(breaker.admit(CORE)).isInstanceOf(BackendAdmission.Refused.class);
    probe.answered(200);

    assertThat(breaker.admit(CORE)).isInstanceOf(BackendAdmission.Granted.class);
    assertThat(
            meters
                .get("edge.backend.breaker.state")
                .tags("backend", "core:8080", "state", "closed")
                .gauge()
                .value())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("A call released without an outcome gives its probe slot back and records nothing")
  void releasedCallRecordsNothing() {
    failThreeTimes(CORE);
    clock.advance(OPEN.plusMillis(1));

    granted(CORE).released();

    BackendCall probe = granted(CORE);
    probe.answered(200);
    assertThat(breaker.admit(CORE)).isInstanceOf(BackendAdmission.Granted.class);
  }

  @Test
  @DisplayName("A call records one outcome: a later failure or release of the same call is ignored")
  void oneOutcomePerCall() {
    for (int i = 0; i < 3; i++) {
      BackendCall call = granted(CORE);
      call.answered(200);
      call.failed(new IOException("the stream broke after the headers"));
      call.released();
    }
    assertThat(breaker.admit(CORE)).isInstanceOf(BackendAdmission.Granted.class);
  }

  @Test
  @DisplayName("Routes to the same backend share one breaker; an unknown backend is a bug")
  void oneBreakerPerBackend() {
    failThreeTimes(IDENTITY);

    assertThat(breaker.admit(URI.create("http://identity:8083/")))
        .isInstanceOf(BackendAdmission.Refused.class);
    assertThat(breaker.admit(CORE)).isInstanceOf(BackendAdmission.Granted.class);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> breaker.admit(URI.create("http://elsewhere:9999")));
  }

  private void failThreeTimes(URI backend) {
    for (int i = 0; i < 3; i++) {
      granted(backend).failed(new IOException("Connection refused"));
    }
  }

  private void answer(URI backend, int status) {
    granted(backend).answered(status);
  }

  private BackendCall granted(URI backend) {
    BackendAdmission admission = breaker.admit(backend);
    assertThat(admission).isInstanceOf(BackendAdmission.Granted.class);
    return ((BackendAdmission.Granted) admission).call();
  }

  /** A clock the test moves forward. */
  private static final class MovableClock extends Clock {

    private Instant now = Instant.parse("2026-11-13T12:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
