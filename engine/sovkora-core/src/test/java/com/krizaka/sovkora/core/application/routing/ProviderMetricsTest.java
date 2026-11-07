package com.krizaka.sovkora.core.application.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderMetricsTest {

  @Test
  @DisplayName("two metrics are equal when their vectors hold the same numbers")
  void comparesVectorContent() {
    var a = new ProviderMetrics(0.9, 120, new float[] {1f, 2f});
    var b = new ProviderMetrics(0.9, 120, new float[] {1f, 2f});
    var c = new ProviderMetrics(0.9, 120, new float[] {1f, 3f});
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c);
    assertThat(a.toString()).contains("[1.0, 2.0]");
  }
}
