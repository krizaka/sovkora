package com.krizaka.sovkora.studio.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DispatchStallPropertiesTest {

  @Test
  @DisplayName("a timeout and a batch inside the bounds are kept")
  void keepsValuesInsideTheBounds() {
    DispatchStallProperties properties = new DispatchStallProperties(Duration.ofMinutes(5), 50);

    assertThat(properties.timeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(properties.batchSize()).isEqualTo(50);
  }

  @Test
  @DisplayName("a timeout under the floor or over the ceiling is refused")
  void refusesATimeoutOutsideTheBounds() {
    assertThatThrownBy(() -> new DispatchStallProperties(Duration.ofSeconds(5), 50))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DispatchStallProperties(Duration.ofHours(2), 50))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("an empty or unbounded batch is refused")
  void refusesABatchOutsideTheBounds() {
    assertThatThrownBy(() -> new DispatchStallProperties(Duration.ofMinutes(5), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DispatchStallProperties(Duration.ofMinutes(5), 501))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
