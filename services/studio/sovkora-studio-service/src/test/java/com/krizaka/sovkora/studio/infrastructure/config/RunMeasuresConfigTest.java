package com.krizaka.sovkora.studio.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("RunMeasuresConfig — the stuck-runs gauge")
class RunMeasuresConfigTest {

  private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
  private static final Timestamp CUTOFF = Timestamp.from(NOW.minus(Duration.ofMinutes(30)));

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  private void bind() {
    new RunMeasuresConfig()
        .stuckRunsMeter(
            jdbc,
            new RunMeasuresProperties(Duration.ofMinutes(30)),
            Clock.fixed(NOW, ZoneOffset.UTC))
        .bindTo(registry);
  }

  @Test
  @DisplayName("sovkora.run.stuck counts the runs still RUNNING past the declared age, no label")
  void stuckGauge() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq("RUNNING"), eq(CUTOFF), any()))
        .thenReturn(2L);

    bind();

    assertThat(registry.get("sovkora.run.stuck").gauge().value()).isEqualTo(2.0);
    assertThat(registry.get("sovkora.run.stuck").gauge().getId().getTags()).isEmpty();
  }

  @Test
  @DisplayName("a database that cannot answer reads as no data, never as zero stuck runs")
  void unreadableIsNotZero() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
        .thenThrow(new DataAccessResourceFailureException("down"));

    bind();

    assertThat(registry.get("sovkora.run.stuck").gauge().value()).isNaN();
  }
}
