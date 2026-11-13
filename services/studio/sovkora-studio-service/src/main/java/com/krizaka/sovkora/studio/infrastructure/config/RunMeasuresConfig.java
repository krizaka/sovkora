package com.krizaka.sovkora.studio.infrastructure.config;

import com.krizaka.sovkora.jobs.api.domain.model.DataClass;
import com.krizaka.sovkora.studio.api.domain.model.RunStatus;
import com.krizaka.sovkora.studio.infrastructure.support.RunMeasureRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.sql.Timestamp;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Binds the stuck-runs gauge, read from the database each time the exporter collects. */
@Configuration
@EnableConfigurationProperties(RunMeasuresProperties.class)
class RunMeasuresConfig {

  /**
   * A stuck run is stuck whatever it carries, so every data class is named: the measure is a count
   * that exposes no content, and a protected run holds a credit hold and a slot like any other
   * (ADR-055 asks the aggregate to say which class it counts).
   */
  private static final String ALL_CLASSES =
      java.util.Arrays.stream(DataClass.values())
          .map(c -> "'" + c.name() + "'")
          .collect(java.util.stream.Collectors.joining(", "));

  private static final Logger logger = LoggerFactory.getLogger(RunMeasuresConfig.class);

  /**
   * The runs still {@code RUNNING} after {@code stuckAfter}. A database that cannot answer reads as
   * NaN (no data), never as zero: an alert must not be silenced by the thing it watches.
   *
   * @param jdbc the studio's database
   * @param properties how old a running run must be to count
   * @param clock the one clock the sweepers read
   * @return the binder Spring Boot registers on every meter registry
   */
  @Bean
  MeterBinder stuckRunsMeter(JdbcTemplate jdbc, RunMeasuresProperties properties, Clock clock) {
    return registry ->
        Gauge.builder(
                RunMeasureRegistry.STUCK,
                () -> {
                  try {
                    Long stuck =
                        jdbc.queryForObject(
                            "SELECT count(*) FROM studio_run WHERE finished_at IS NULL"
                                + " AND parent_run_id IS NULL AND status = ? AND started_at < ?"
                                + " AND data_class IN ("
                                + ALL_CLASSES
                                + ")",
                            Long.class,
                            RunStatus.RUNNING.name(),
                            Timestamp.from(clock.instant().minus(properties.stuckAfter())));
                    return stuck == null ? Double.NaN : stuck.doubleValue();
                  } catch (org.springframework.dao.DataAccessException unreadable) {
                    logger.warn("The stuck-runs measure could not read the database", unreadable);
                    return Double.NaN;
                  }
                })
            .description("Runs still RUNNING past the declared age")
            .register(registry);
  }
}
