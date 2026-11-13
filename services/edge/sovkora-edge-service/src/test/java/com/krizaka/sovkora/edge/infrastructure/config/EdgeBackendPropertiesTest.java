package com.krizaka.sovkora.edge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.krizaka.sovkora.edge.infrastructure.config.EdgeBackendProperties.Breaker;
import com.krizaka.sovkora.edge.infrastructure.config.EdgeBackendProperties.Timeouts;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EdgeBackendPropertiesTest {

  private static final Timeouts TIMEOUTS =
      new Timeouts(Duration.ofSeconds(2), Duration.ofSeconds(50));
  private static final Breaker BREAKER =
      new Breaker(50, Duration.ofSeconds(20), 80, 20, 10, Duration.ofSeconds(30), 3);

  @Test
  @DisplayName("The shipped defaults are inside every bound")
  void defaultsAreValid() {
    var properties = new EdgeBackendProperties(TIMEOUTS, BREAKER, Duration.ofSeconds(5));

    assertThat(properties.timeouts().response()).isEqualTo(Duration.ofSeconds(50));
    assertThat(properties.breaker().minimumCalls()).isEqualTo(10);
    assertThat(properties.retryAfter()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("A timeout outside its bounds, or missing, is refused at startup")
  void timeoutsOutOfRangeAreRefused() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Timeouts(Duration.ofMillis(99), Duration.ofSeconds(60)))
        .withMessageContaining("connect");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Timeouts(Duration.ofSeconds(31), Duration.ofSeconds(60)))
        .withMessageContaining("connect");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Timeouts(Duration.ofSeconds(2), Duration.ofMinutes(11)))
        .withMessageContaining("response");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Timeouts(Duration.ofSeconds(2), null))
        .withMessageContaining("response");
  }

  @Test
  @DisplayName("A breaker rate, window or duration outside its bounds is refused at startup")
  void breakerOutOfRangeIsRefused() {
    Duration slow = Duration.ofSeconds(30);
    Duration open = Duration.ofSeconds(30);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Breaker(0, slow, 80, 20, 10, open, 3))
        .withMessageContaining("failure-rate-percent");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Breaker(101, slow, 80, 20, 10, open, 3))
        .withMessageContaining("failure-rate-percent");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Breaker(50, slow, 0, 20, 10, open, 3))
        .withMessageContaining("slow-call-rate-percent");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Breaker(50, Duration.ofMillis(50), 80, 20, 10, open, 3))
        .withMessageContaining("slow-call-threshold");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Breaker(50, slow, 80, 1, 1, open, 3))
        .withMessageContaining("window-calls");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Breaker(50, slow, 80, 20, 21, open, 3))
        .withMessageContaining("minimum-calls");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Breaker(50, slow, 80, 20, 10, Duration.ofMillis(500), 3))
        .withMessageContaining("open-duration");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Breaker(50, slow, 80, 20, 10, open, 0))
        .withMessageContaining("half-open-calls");
  }

  @Test
  @DisplayName("The default Retry-After is between one second and one hour")
  void retryAfterOutOfRangeIsRefused() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EdgeBackendProperties(TIMEOUTS, BREAKER, Duration.ofMillis(500)))
        .withMessageContaining("retry-after");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EdgeBackendProperties(TIMEOUTS, BREAKER, Duration.ofHours(2)))
        .withMessageContaining("retry-after");
  }
}
