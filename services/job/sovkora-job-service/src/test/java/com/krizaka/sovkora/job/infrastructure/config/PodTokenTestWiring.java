package com.krizaka.sovkora.job.infrastructure.config;

import com.krizaka.sovkora.job.application.service.PodTokenService;
import com.krizaka.sovkora.job.domain.model.PodTokenPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * The pod token's wiring on the test container's PostgreSQL: {@code gpu_pod_tokens} exactly as
 * {@code V4} creates it, the job service's own signer ({@link PodTokenConfiguration}), and a clock
 * the test moves.
 */
@Configuration
@Import(PodTokenConfiguration.class)
public class PodTokenTestWiring {

  /** The session secret every service of the test shares (and the pod token secret's fallback). */
  public static final String IDENTITY_SECRET = "a-test-identity-secret-of-at-least-32-chars!";

  public static final Instant T0 = Instant.parse("2026-11-14T18:00:00Z");

  @Bean
  DataSource dataSource(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String user,
      @Value("${spring.datasource.password}") String password) {
    return new DriverManagerDataSource(url, user, password);
  }

  @Bean
  JdbcTemplate jdbcTemplate(DataSource dataSource) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP TABLE IF EXISTS gpu_pod_tokens");
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V4__gpu_pod_token.sql"))
        .execute(dataSource);
    return jdbc;
  }

  @Bean
  public static PodTokenProperties podTokenProperties() {
    // The deployment's fallback: no JOB_POD_TOKEN_SECRET, so the identity secret, under the label.
    return new PodTokenProperties(IDENTITY_SECRET, Duration.parse("PT12H30M"));
  }

  @Bean
  MutableClock clock() {
    return new MutableClock(T0);
  }

  @Bean
  PodTokenService podTokenService(
      JdbcTemplate jdbc, JwtEncoder podTokenEncoder, PodTokenPolicy policy, Clock clock) {
    return new PodTokenService(jdbc, podTokenEncoder, policy, clock);
  }

  /** A clock the test sets. */
  public static final class MutableClock extends Clock {

    private volatile Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    public void set(Instant instant) {
      now = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
