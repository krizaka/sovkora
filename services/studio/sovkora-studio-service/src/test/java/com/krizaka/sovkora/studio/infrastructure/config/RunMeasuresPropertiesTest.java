package com.krizaka.sovkora.studio.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RunMeasuresProperties — when a run is called stuck")
class RunMeasuresPropertiesTest {

  @Test
  @DisplayName("an age under the dispatch ceiling or over the run ceiling is refused")
  void bounded() {
    assertThat(new RunMeasuresProperties(Duration.ofMinutes(30)).stuckAfter())
        .isEqualTo(Duration.ofMinutes(30));
    assertThatThrownBy(() -> new RunMeasuresProperties(Duration.ofSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RunMeasuresProperties(Duration.ofHours(5)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
