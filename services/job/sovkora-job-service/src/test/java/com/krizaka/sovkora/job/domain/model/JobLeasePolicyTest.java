package com.krizaka.sovkora.job.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The lease setting is bounded: a value outside the range stops the boot, it is never clamped. */
class JobLeasePolicyTest {

  @Test
  @DisplayName("a lease under 30s or over 1h is refused")
  void leaseBounds() {
    assertThatThrownBy(() -> new JobLeasePolicy(Duration.ofSeconds(29), Duration.ofSeconds(5), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lease");
    assertThatThrownBy(
            () -> new JobLeasePolicy(Duration.ofSeconds(3601), Duration.ofSeconds(30), 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new JobLeasePolicy(Duration.ofSeconds(30), Duration.ofSeconds(10), 0).lease())
        .isEqualTo(Duration.ofSeconds(30));
    assertThat(new JobLeasePolicy(Duration.ofHours(1), Duration.ofSeconds(30), 0).lease())
        .isEqualTo(Duration.ofHours(1));
  }

  @Test
  @DisplayName(
      "a renewal slower than a third of the lease is refused: one missed beat would fail a live job")
  void renewalBound() {
    assertThatThrownBy(() -> new JobLeasePolicy(Duration.ofSeconds(120), Duration.ofSeconds(41), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("renew-interval");
    assertThatThrownBy(() -> new JobLeasePolicy(Duration.ofSeconds(120), Duration.ZERO, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("the retry cap is 0 to 5")
  void retryBounds() {
    assertThatThrownBy(
            () -> new JobLeasePolicy(Duration.ofSeconds(120), Duration.ofSeconds(30), -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("max-retries");
    assertThatThrownBy(() -> new JobLeasePolicy(Duration.ofSeconds(120), Duration.ofSeconds(30), 6))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new JobLeasePolicy(Duration.ofSeconds(120), Duration.ofSeconds(30), 5).maxRetries())
        .isEqualTo(5);
    assertThat(new JobLeasePolicy(Duration.ofSeconds(120), Duration.ofSeconds(30), 0).maxRetries())
        .isZero();
  }
}
