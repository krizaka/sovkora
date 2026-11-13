package com.krizaka.sovkora.studio.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("RunMeasuresConfig — the stuck-runs gauge")
class RunMeasuresConfigTest {

  private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

  @Test
  @DisplayName(
      "sovkora.run.stuck is the count of runs still RUNNING past the declared age, with no label")
  void stuckGauge() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Timestamp cutoff = Timestamp.from(NOW.minus(Duration.ofMinutes(30)));
    when(jdbc.queryForObject(any(String.class), eq(Long.class), eq("RUNNING"), eq(cutoff)))
        .thenReturn(2L);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    new RunMeasuresConfig()
        .stuckRunsMeter(
            jdbc,
            new RunMeasuresProperties(Duration.ofMinutes(30)),
            Clock.fixed(NOW, ZoneOffset.UTC))
        .bindTo(registry);

    assertThat(registry.get("sovkora.run.stuck").gauge().value()).isEqualTo(2.0);
    assertThat(registry.get("sovkora.run.stuck").gauge().getId().getTags()).isEmpty();
  }

  @Test
  @DisplayName("a database that cannot answer reads as no data, never as zero stuck runs")
  void unreadableIsNotZero() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(any(String.class), eq(Long.class), any(), any()))
        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    new RunMeasuresConfig()
        .stuckRunsMeter(
            jdbc,
            new RunMeasuresProperties(Duration.ofMinutes(30)),
            Clock.fixed(NOW, ZoneOffset.UTC))
        .bindTo(registry);

    assertThat(registry.get("sovkora.run.stuck").gauge().value()).isNaN();
  }
}
