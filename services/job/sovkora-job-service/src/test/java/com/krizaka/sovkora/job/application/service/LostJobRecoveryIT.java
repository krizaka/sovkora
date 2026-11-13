package com.krizaka.sovkora.job.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.engine.persistence.infrastructure.config.MessagingContract;
import com.krizaka.sovkora.job.domain.model.JobLeasePolicy;
import com.krizaka.sovkora.jobs.api.domain.model.CapabilityDeclaration;
import com.krizaka.sovkora.jobs.api.domain.model.DataClass;
import com.krizaka.sovkora.jobs.api.domain.model.JobCommand;
import com.krizaka.sovkora.persistence.api.domain.model.OutboxMessage;
import com.krizaka.sovkora.persistence.api.domain.model.PendingOutboxEvent;
import com.krizaka.sovkora.persistence.api.domain.ports.inbound.CapabilityManager;
import com.krizaka.sovkora.persistence.api.domain.ports.inbound.OutboxStore;
import com.krizaka.sovkora.test.support.AbstractContainerIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * A job whose worker died reaches a terminal state (krizaka-internal#669), against the real {@code
 * orazaka_jobs} table of {@code V1} + {@code V3} on a real PostgreSQL.
 *
 * <p>Time is the test's: the lease and the sweeper both read one injected {@link Clock}, so "the
 * worker crashed" is a clock that moved past the lease with no renewal — nothing sleeps.
 */
@SpringJUnitConfig(LostJobRecoveryIT.Wiring.class)
class LostJobRecoveryIT extends AbstractContainerIntegrationTest {

  private static final Instant T0 = Instant.parse("2026-11-13T10:00:00Z");
  private static final String ACTOR = "550e8400-e29b-41d4-a716-446655440669";
  private static final String FEATURE = "orazaka.studio.validation";
  private static final String ROUTING_KEY = "job.text.generate";
  private static final String DOCUMENT_TEXT = "Bail: le locataire Jean Tremblay, 123 rue Exemple";
  private static final int CAP = 2;

  @Autowired JdbcTemplate jdbc;
  @Autowired JobLeaseService leases;
  @Autowired LostJobRecoveryService recovery;
  @Autowired Wiring.MutableClock clock;
  @Autowired Wiring.CapturingOutbox outbox;
  @Autowired PlatformTransactionManager transactions;

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM orazaka_jobs");
    outbox.messages.clear();
    clock.set(T0);
  }

  @Test
  @DisplayName(
      "a worker that dies: the lease expires, the job is re-enqueued, then failed after the cap")
  void crashedWorkerJobIsReEnqueuedThenFailedAfterTheCap() {
    String id = processing("hold-1");
    leases.claim(command(id, "hold-1"));

    // Attempt 1 and 2: the worker dies each time, nothing renews.
    for (int attempt = 1; attempt <= CAP; attempt++) {
      clock.advance(Duration.ofSeconds(121));
      assertThat(recovery.recoverExpired()).isEqualTo(1);
      assertThat(status(id)).as("re-enqueued, not left RUNNING").isEqualTo("PENDING");
      assertThat(intOf(id, "retry_count")).isEqualTo(attempt);
      assertThat(stringOf(id, "locked_by_worker")).isNull();
      OutboxMessage requeued = outbox.messages.getLast();
      assertThat(requeued.exchange()).isEqualTo(MessagingContract.JOBS_EXCHANGE);
      assertThat(requeued.routingKey()).isEqualTo(ROUTING_KEY);
      JobCommand republished = (JobCommand) requeued.payload();
      assertThat(republished.jobId()).isEqualTo(id);
      assertThat(republished.holdId()).isEqualTo("hold-1");
      assertThat(republished.dataClass()).isEqualTo(DataClass.SENSITIVE);
      // The next worker picks it up again and dies again.
      jdbc.update("UPDATE orazaka_jobs SET status = 'PROCESSING' WHERE id = ?", id);
      leases.claim(republished);
    }

    // Attempt 3: the cap is spent, the job fails with a code and the hold is released.
    outbox.messages.clear();
    clock.advance(Duration.ofSeconds(121));
    assertThat(recovery.recoverExpired()).isEqualTo(1);

    assertThat(status(id)).isEqualTo("FAILED");
    assertThat(stringOf(id, "error_message")).isEqualTo("JOB_WORKER_LOST");
    assertThat(outbox.messages).hasSize(1);
    OutboxMessage failure = outbox.messages.getFirst();
    assertThat(failure.exchange()).isEqualTo(MessagingContract.EVENTS_EXCHANGE);
    assertThat(failure.routingKey()).isEqualTo("job." + id + ".error");
    @SuppressWarnings("unchecked")
    Map<String, Object> event = (Map<String, Object>) failure.payload();
    assertThat(event)
        .containsEntry("jobId", id)
        .containsEntry("error", "JOB_WORKER_LOST")
        .containsEntry("cause", "EXECUTOR_FAULT")
        .containsEntry("holdId", "hold-1");
    assertThat(event.toString()).doesNotContain("Tremblay");
    assertThat(stringOf(id, "error_message")).doesNotContain("Tremblay");
  }

  @Test
  @DisplayName("a live heartbeat keeps the job")
  void renewedLeaseKeepsTheJob() {
    String id = processing(null);
    leases.claim(command(id, null));

    clock.advance(Duration.ofSeconds(100));
    assertThat(leases.renew(List.of(id))).isEqualTo(1);
    clock.advance(Duration.ofSeconds(100)); // 200s after the claim, 100s after the renewal

    assertThat(recovery.recoverExpired()).isZero();
    assertThat(status(id)).isEqualTo("PROCESSING");
    assertThat(outbox.messages).isEmpty();
  }

  @Test
  @DisplayName("a worker only renews the jobs it holds")
  void renewalIgnoresAJobAnotherWorkerHolds() {
    String mine = processing(null);
    leases.claim(command(mine, null));
    String theirs = processing(null);
    jdbc.update(
        "UPDATE orazaka_jobs SET locked_by_worker = 'another-worker', lease_expires_at = ? WHERE id = ?",
        java.sql.Timestamp.from(T0.plusSeconds(60)),
        theirs);

    clock.advance(Duration.ofSeconds(30));

    assertThat(leases.renew(List.of(mine, theirs))).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                    "SELECT lease_expires_at FROM orazaka_jobs WHERE id = ?",
                    java.sql.Timestamp.class,
                    theirs)
                .toInstant())
        .isEqualTo(T0.plusSeconds(60));
  }

  @Test
  @DisplayName("a job that died before its first claim is recovered from its last update")
  void unclaimedProcessingJobIsRecoveredFromItsUpdatedAt() {
    String stale = processing(null);
    String fresh = processing(null);
    jdbc.update(
        "UPDATE orazaka_jobs SET updated_at = ? WHERE id = ?",
        java.sql.Timestamp.from(T0.minusSeconds(300)),
        stale);
    jdbc.update(
        "UPDATE orazaka_jobs SET updated_at = ? WHERE id = ?",
        java.sql.Timestamp.from(T0.minusSeconds(10)),
        fresh);

    assertThat(recovery.recoverExpired()).isEqualTo(1);

    assertThat(status(stale)).isEqualTo("PENDING");
    assertThat(status(fresh)).isEqualTo("PROCESSING");
  }

  @Test
  @DisplayName("a job whose capability is gone fails at once instead of being re-published nowhere")
  void unknownCapabilityFailsTheJob() {
    String id = processing("hold-9");
    jdbc.update("UPDATE orazaka_jobs SET feature_key = 'gone.capability' WHERE id = ?", id);
    leases.claim(
        new JobCommand(id, ACTOR, "gone.capability", "default", Map.of(), DataClass.STANDARD));
    clock.advance(Duration.ofSeconds(121));

    assertThat(recovery.recoverExpired()).isEqualTo(1);

    assertThat(status(id)).isEqualTo("FAILED");
    assertThat(outbox.messages.getFirst().routingKey()).isEqualTo("job." + id + ".error");
  }

  @Test
  @DisplayName("a job recovered and failed is purged by retention like any failed job")
  void failedOrphanIsCoveredByRetention(@TempDir Path uploads) throws Exception {
    String id = processing(null);
    jdbc.update("UPDATE orazaka_jobs SET retry_count = ? WHERE id = ?", CAP, id);
    leases.claim(command(id, null));
    Path directory = Files.createDirectories(uploads.resolve(ACTOR).resolve(id));
    Files.writeString(directory.resolve("input.json"), "x");
    clock.advance(Duration.ofSeconds(121));
    recovery.recoverExpired();
    jdbc.update(
        "UPDATE orazaka_jobs SET updated_at = now() - interval '400 days' WHERE id = ?", id);

    int purged =
        new JobRetentionService(
                jdbc,
                new com.krizaka.sovkora.engine.assets.infrastructure.storage.NoDurableAssetStore(),
                new com.krizaka.sovkora.engine.assets.infrastructure.config.MediaStorageProperties(
                    "local", null, null, "storage.bunnycdn.com", null, "default"))
            .purgeExpired(uploads);

    assertThat(purged).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orazaka_jobs WHERE id = ?", Integer.class, id))
        .isZero();
    assertThat(Files.exists(directory)).isFalse();
  }

  @Test
  @DisplayName("two sweepers never take the same job")
  void twoSweepersNeverTakeTheSameJob() throws Exception {
    int jobs = 30;
    for (int i = 0; i < jobs; i++) {
      String id = processing(null);
      leases.claim(command(id, null));
    }
    clock.advance(Duration.ofSeconds(121));

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<Integer>> sweeps = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      sweeps.add(
          pool.submit(
              () -> {
                go.await();
                return recovery.recoverExpired();
              }));
    }
    go.countDown();
    int taken = 0;
    for (Future<Integer> sweep : sweeps) {
      taken += sweep.get(30, TimeUnit.SECONDS);
    }
    pool.shutdownNow();

    assertThat(taken).as("every job taken exactly once across the two sweepers").isEqualTo(jobs);
    assertThat(outbox.messages).as("one re-publish per job").hasSize(jobs);
    assertThat(outbox.messages.stream().map(m -> ((JobCommand) m.payload()).jobId()).distinct())
        .hasSize(jobs);
    assertThat(jdbc.queryForObject("SELECT sum(retry_count) FROM orazaka_jobs", Integer.class))
        .isEqualTo(jobs);
  }

  @Test
  @DisplayName("a sweeper skips a job another sweeper has locked instead of waiting for it")
  void aLockedJobIsSkippedNotAwaited() {
    String locked = processing(null);
    String free = processing(null);
    leases.claim(command(locked, null));
    leases.claim(command(free, null));
    clock.advance(Duration.ofSeconds(121));

    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              jdbc.queryForList("SELECT id FROM orazaka_jobs WHERE id = ? FOR UPDATE", locked);
              int taken =
                  assertTimeoutPreemptively(
                      Duration.ofSeconds(10),
                      () -> {
                        // The sweeper runs on its own connection, as a second instance would.
                        ExecutorService other = Executors.newSingleThreadExecutor();
                        try {
                          return other.submit(() -> recovery.recoverExpired()).get();
                        } finally {
                          other.shutdownNow();
                        }
                      });
              assertThat(taken).isEqualTo(1);
              assertThat(status(free)).isEqualTo("PENDING");
            });

    // Nothing of the locked job was touched.
    assertThat(status(locked)).isEqualTo("PROCESSING");
  }

  // ── fixtures ────────────────────────────────────────────────────────────

  private String processing(String holdId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO orazaka_jobs (id, user_id, feature_key, status, payload, data_class, updated_at)"
            + " VALUES (?, ?, ?, 'PROCESSING', ?::jsonb, 'SENSITIVE', ?)",
        id,
        ACTOR,
        FEATURE,
        "{\"text\":\"" + DOCUMENT_TEXT + "\"}",
        java.sql.Timestamp.from(T0));
    return id;
  }

  private JobCommand command(String id, String holdId) {
    JobCommand command =
        new JobCommand(
            id, ACTOR, FEATURE, "default", Map.of("text", DOCUMENT_TEXT), DataClass.SENSITIVE);
    return holdId == null ? command : command.withReservation(holdId, id);
  }

  private String status(String id) {
    return stringOf(id, "status");
  }

  private String stringOf(String id, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM orazaka_jobs WHERE id = ?", String.class, id);
  }

  private int intOf(String id, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM orazaka_jobs WHERE id = ?", Integer.class, id);
  }

  /** The real baseline and the real lease migration, on the container's database. */
  @Configuration
  @EnableTransactionManagement
  static class Wiring {

    private static final java.util.regex.Pattern PURGE_TABLE =
        java.util.regex.Pattern.compile("(?s)CREATE TABLE orazaka_job_purge \\(.*?\\n\\);");

    private static final java.util.regex.Pattern JOBS_TABLE =
        java.util.regex.Pattern.compile("(?s)CREATE TABLE orazaka_jobs \\(.*?\\n\\);");

    @Bean
    DataSource dataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String user,
        @Value("${spring.datasource.password}") String password) {
      return new DriverManagerDataSource(url, user, password);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) throws Exception {
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      String baseline = resource("db/migration/V1__job_baseline.sql");
      java.util.regex.Matcher table = JOBS_TABLE.matcher(baseline);
      if (!table.find()) {
        throw new IllegalStateException("orazaka_jobs not found in V1");
      }
      java.util.regex.Matcher purge = PURGE_TABLE.matcher(baseline);
      if (!purge.find()) {
        throw new IllegalStateException("orazaka_job_purge not found in V1");
      }
      jdbc.execute("DROP TABLE IF EXISTS orazaka_job_purge");
      jdbc.execute("DROP TABLE IF EXISTS orazaka_jobs");
      jdbc.execute(table.group());
      jdbc.execute(purge.group());
      jdbc.execute(resource("db/migration/V3__job_lease.sql"));
      return jdbc;
    }

    private static String resource(String path) throws Exception {
      try (var in = Wiring.class.getClassLoader().getResourceAsStream(path)) {
        return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      }
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    MutableClock clock() {
      return new MutableClock(T0);
    }

    @Bean
    JobLeasePolicy leasePolicy() {
      return new JobLeasePolicy(Duration.ofSeconds(120), Duration.ofSeconds(30), CAP);
    }

    @Bean
    CapturingOutbox outbox() {
      return new CapturingOutbox();
    }

    @Bean
    CapabilityManager capabilities() {
      CapabilityManager manager = mock(CapabilityManager.class);
      when(manager.findByFeatureKey(FEATURE))
          .thenReturn(
              Optional.of(
                  new CapabilityDeclaration(
                      FEATURE, "text", ROUTING_KEY, null, null, "INTERACTIVE", null, null, true)));
      return manager;
    }

    @Bean
    JobLeaseService jobLeaseService(JdbcTemplate jdbc, MutableClock clock, JobLeasePolicy props) {
      return new JobLeaseService(jdbc, clock, props, "worker-under-test");
    }

    @Bean
    LostJobRecoveryService lostJobRecoveryService(
        JdbcTemplate jdbc,
        CapturingOutbox outbox,
        CapabilityManager capabilities,
        MutableClock clock,
        JobLeasePolicy props) {
      return new LostJobRecoveryService(
          jdbc, outbox, capabilities, new ObjectMapper(), clock, props);
    }

    static final class CapturingOutbox implements OutboxStore {
      final List<OutboxMessage> messages = new CopyOnWriteArrayList<>();

      @Override
      public void append(OutboxMessage message) {
        messages.add(message);
      }

      @Override
      public List<PendingOutboxEvent> lockPendingBatch(int batchSize) {
        return List.of();
      }

      @Override
      public void markPublished(UUID eventId) {}

      @Override
      public void recordFailure(UUID eventId, int previousAttempts) {}

      @Override
      public long purgePublishedBefore(Instant cutoff) {
        return 0;
      }
    }

    static final class MutableClock extends Clock {
      private volatile Instant now;

      MutableClock(Instant start) {
        this.now = start;
      }

      void set(Instant instant) {
        this.now = instant;
      }

      void advance(Duration duration) {
        this.now = now.plus(duration);
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
}
