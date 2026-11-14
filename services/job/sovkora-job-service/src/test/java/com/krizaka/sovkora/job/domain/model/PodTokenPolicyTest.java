package com.krizaka.sovkora.job.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PodTokenPolicyTest {

  private final PodTokenPolicy policy = new PodTokenPolicy(Duration.ofHours(12));

  @Test
  @DisplayName("the pod's lifetime cap is kept, and capped to the service's maximum")
  void lifetime() {
    assertThat(policy.lifetime(Duration.ofHours(8))).isEqualTo(Duration.ofHours(8));
    assertThat(policy.lifetime(Duration.ofDays(2))).isEqualTo(Duration.ofHours(12));
    assertThatThrownBy(() -> policy.lifetime(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("the pod role is never SERVICE, and the maximum is bounded at boot")
  void bounds() {
    assertThat(PodTokenPolicy.ROLE).isNotEqualTo("SERVICE");
    assertThatThrownBy(() -> new PodTokenPolicy(Duration.ofDays(2)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PodTokenPolicy(Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
