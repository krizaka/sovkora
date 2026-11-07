package com.krizaka.sovkora.tools.infrastructure.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryRateGateTest {

  @Test
  @DisplayName("a budget per key and minute: spent, refused, renewed the next minute")
  void budgetPerMinute() {
    InMemoryRateGate gate = new InMemoryRateGate();
    Instant t = Instant.parse("2026-10-23T12:00:10Z");
    assertThat(gate.tryAcquire("host:a", 2, t)).isTrue();
    assertThat(gate.tryAcquire("host:a", 2, t)).isTrue();
    assertThat(gate.tryAcquire("host:a", 2, t)).isFalse();
    assertThat(gate.tryAcquire("host:b", 2, t)).isTrue();
    assertThat(gate.tryAcquire("host:a", 2, t.plusSeconds(60))).isTrue();
  }
}
