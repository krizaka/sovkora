package com.krizaka.sovkora.studio.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.test.container.AbstractContainerIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The stuck-runs gauge against a real PostgreSQL: its {@code data_class = ANY (?)} binding of a
 * Java array, which a mocked {@code JdbcTemplate} cannot prove. The schema is the one Flyway
 * builds.
 */
@DisplayName("RunMeasuresConfig — the stuck-runs gauge on PostgreSQL")
class RunMeasuresConfigIT extends AbstractContainerIntegrationTest {

  private static final String DATABASE = "run_measures_check";
  private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
  private static final Duration STUCK_AFTER = Duration.ofMinutes(30);

  private static HikariDataSource dataSource;
  private static JdbcTemplate jdbc;

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  private static String url(String database) {
    String url = postgres().getJdbcUrl();
    return url.substring(0, url.lastIndexOf('/') + 1) + database;
  }

  @BeforeAll
  static void migrate() {
    new JdbcTemplate(pooled(postgres().getJdbcUrl()))
        .execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
    new JdbcTemplate(pooled(postgres().getJdbcUrl())).execute("CREATE DATABASE " + DATABASE);
    dataSource = pooled(url(DATABASE));
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    // A REGULATED run needs an installation (its consent); the gauge does not read it, so the
    // fixture drops that one row-level guard rather than seeding a catalogue chain.
    jdbc.execute("ALTER TABLE studio_run DROP CONSTRAINT ck_run_uninstalled_not_regulated");
  }

  private static HikariDataSource pooled(String jdbcUrl) {
    HikariDataSource pool = new HikariDataSource();
    pool.setJdbcUrl(jdbcUrl);
    pool.setUsername(postgres().getUsername());
    pool.setPassword(postgres().getPassword());
    pool.setMaximumPoolSize(2);
    return pool;
  }

  @AfterAll
  static void close() {
    if (dataSource != null) {
      dataSource.close();
    }
  }

  @AfterEach
  void emptyTheTable() {
    jdbc.update("DELETE FROM studio_run");
  }

  private void run(String status, String dataClass, Duration age, boolean finished, boolean child) {
    java.util.UUID parent = null;
    if (child) {
      parent = java.util.UUID.randomUUID();
      insert(parent, "SUCCEEDED", "STANDARD", Duration.ofHours(5), true, null);
    }
    insert(java.util.UUID.randomUUID(), status, dataClass, age, finished, parent);
  }

  private void insert(
      java.util.UUID id,
      String status,
      String dataClass,
      Duration age,
      boolean finished,
      java.util.UUID parent) {
    java.sql.Timestamp started = java.sql.Timestamp.from(NOW.minus(age));
    jdbc.update(
        "INSERT INTO studio_run (id, actor_id, studio_key, blueprint_version, status,"
            + " correlation_id, data_class, parent_run_id, started_at, finished_at)"
            + " VALUES (?, 'actor', 'studio', '1.0.0', ?, ?, ?, ?, ?, ?)",
        id,
        status,
        id.toString(),
        dataClass,
        parent,
        started,
        finished ? started : null);
  }

  private double stuck() {
    new RunMeasuresConfig()
        .stuckRunsMeter(
            jdbc, new RunMeasuresProperties(STUCK_AFTER), Clock.fixed(NOW, ZoneOffset.UTC))
        .bindTo(registry);
    return registry.get("sovkora.run.stuck").gauge().value();
  }

  @Test
  @DisplayName("counts the stuck runs of every data class, and only those")
  void countsEveryClassAndOnlyStuckRuns() {
    Duration old = Duration.ofHours(2);
    run("RUNNING", "STANDARD", old, false, false);
    run("RUNNING", "STANDARD", old, false, false);
    run("RUNNING", "SENSITIVE", old, false, false);
    run("RUNNING", "REGULATED", old, false, false);
    // not stuck: too recent, finished, another status, or a child run
    run("RUNNING", "STANDARD", Duration.ofMinutes(5), false, false);
    run("RUNNING", "SENSITIVE", old, true, false);
    run("SUCCEEDED", "REGULATED", old, false, false);
    run("RUNNING", "STANDARD", old, false, true);

    assertThat(stuck()).isEqualTo(4.0);
  }

  @Test
  @DisplayName("each data class counts on its own")
  void eachClassCounts() {
    for (String dataClass : new String[] {"STANDARD", "SENSITIVE", "REGULATED"}) {
      jdbc.update("DELETE FROM studio_run");
      run("RUNNING", dataClass, Duration.ofHours(2), false, false);
      assertThat(stuck()).as(dataClass).isEqualTo(1.0);
    }
  }

  @Test
  @DisplayName("no run at all reads as zero, not as an SQL error or no data")
  void noRunIsZero() {
    assertThat(stuck()).isEqualTo(0.0);
  }
}
