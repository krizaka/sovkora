package com.krizaka.sovkora.job.application.service;

import com.krizaka.sovkora.engine.persistence.infrastructure.config.MessagingContract;
import com.krizaka.sovkora.job.domain.model.JobLeasePolicy;
import com.krizaka.sovkora.jobs.api.domain.model.CapabilityDeclaration;
import com.krizaka.sovkora.jobs.api.domain.model.DataClass;
import com.krizaka.sovkora.jobs.api.domain.model.FailureCause;
import com.krizaka.sovkora.jobs.api.domain.model.JobCommand;
import com.krizaka.sovkora.persistence.api.domain.model.OutboxMessage;
import com.krizaka.sovkora.persistence.api.domain.ports.inbound.CapabilityManager;
import com.krizaka.sovkora.persistence.api.domain.ports.inbound.OutboxStore;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Takes back the jobs whose worker died, so none is left {@code PROCESSING} for ever
 * (krizaka-internal#669).
 *
 * <p>A job is lost when its lease expired: the worker stopped renewing it (out of memory, eviction,
 * hard kill). A job that was started but never claimed — the worker died between the status change
 * and the claim — is lost when its last update is older than a lease.
 *
 * <p>Each lost job is put back, up to the retry cap: the status returns to {@code PENDING} and the
 * command is published again through the outbox, in the same transaction, so the row and the
 * message never disagree. Past the cap the job fails with {@link #ERROR_CODE} and a {@code
 * job.{id}.error} event typed {@code EXECUTOR_FAULT}, carrying the credit hold so billing releases
 * it. That is the path any failure takes; nothing downstream knows this one came from a sweeper.
 *
 * <p>The error is a code, never text: the job's payload is a person's document, and neither the row
 * nor the event may carry any of it (retention rules). The failed row is terminal, so the retention
 * purge covers it like any other.
 *
 * <p>Rows are taken with {@code FOR UPDATE SKIP LOCKED}: two sweepers (two instances, or a sweep
 * that overlaps itself) never take the same job, and neither waits for the other.
 */
@Service
public class LostJobRecoveryService {

  /** The code a job fails with when its worker was lost. */
  public static final String ERROR_CODE = "JOB_WORKER_LOST";

  private static final Logger logger = LoggerFactory.getLogger(LostJobRecoveryService.class);
  private static final int BATCH = 50;
  private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {};

  private final JdbcTemplate jdbcTemplate;
  private final OutboxStore outboxStore;
  private final CapabilityManager capabilityManager;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final JobLeasePolicy policy;

  public LostJobRecoveryService(
      JdbcTemplate jdbcTemplate,
      OutboxStore outboxStore,
      CapabilityManager capabilityManager,
      ObjectMapper objectMapper,
      Clock clock,
      JobLeasePolicy policy) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
    this.outboxStore = Objects.requireNonNull(outboxStore, "OutboxStore cannot be null");
    this.capabilityManager =
        Objects.requireNonNull(capabilityManager, "CapabilityManager cannot be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    this.policy = Objects.requireNonNull(policy, "JobLeasePolicy cannot be null");
  }

  /**
   * Re-enqueues or fails one batch of lost jobs.
   *
   * @return how many jobs this sweep took
   */
  @Transactional
  public int recoverExpired() {
    Instant now = clock.instant();
    List<LostJob> lost =
        jdbcTemplate.query(
            "SELECT id, user_id, feature_key, payload::text, data_class, retry_count, hold_id, model"
                + " FROM orazaka_jobs WHERE status = 'PROCESSING' AND ((lease_expires_at IS NOT NULL"
                + " AND lease_expires_at < ?) OR (lease_expires_at IS NULL AND updated_at < ?))"
                + " ORDER BY updated_at LIMIT ? FOR UPDATE SKIP LOCKED",
            (rs, row) ->
                new LostJob(
                    rs.getString("id"),
                    rs.getString("user_id"),
                    rs.getString("feature_key"),
                    rs.getString("payload"),
                    rs.getString("data_class"),
                    rs.getInt("retry_count"),
                    rs.getString("hold_id"),
                    rs.getString("model")),
            utc(now),
            utc(now.minus(policy.lease())),
            BATCH);
    for (LostJob job : lost) {
      Optional<String> routingKey =
          job.retryCount() < policy.maxRetries()
              ? capabilityManager
                  .findByFeatureKey(job.featureKey())
                  .map(CapabilityDeclaration::routingKey)
              : Optional.empty();
      if (routingKey.isPresent()) {
        reEnqueue(job, routingKey.get(), now);
      } else {
        fail(job, now);
      }
    }
    return lost.size();
  }

  private void reEnqueue(LostJob job, String routingKey, Instant now) {
    jdbcTemplate.update(
        "UPDATE orazaka_jobs SET status = 'PENDING', retry_count = retry_count + 1,"
            + " locked_by_worker = NULL, lease_expires_at = NULL, error_message = NULL,"
            + " updated_at = ? WHERE id = ?",
        utc(now),
        job.id());
    outboxStore.append(
        new OutboxMessage(
            "job",
            job.id(),
            MessagingContract.JOBS_EXCHANGE,
            routingKey,
            job.toCommand(objectMapper)));
    logger.warn(
        "Job {} lost its worker: re-enqueued, attempt {} of {}",
        job.id(),
        job.retryCount() + 1,
        policy.maxRetries());
  }

  private void fail(LostJob job, Instant now) {
    jdbcTemplate.update(
        "UPDATE orazaka_jobs SET status = 'FAILED', error_message = ?, locked_by_worker = NULL,"
            + " lease_expires_at = NULL, updated_at = ? WHERE id = ?",
        ERROR_CODE,
        utc(now),
        job.id());
    Map<String, Object> event = new HashMap<>();
    event.put("jobId", job.id());
    event.put("error", ERROR_CODE);
    event.put("cause", FailureCause.EXECUTOR_FAULT.name());
    if (job.holdId() != null) {
      event.put("holdId", job.holdId());
    }
    outboxStore.append(
        new OutboxMessage(
            "job",
            job.id(),
            MessagingContract.EVENTS_EXCHANGE,
            "job." + job.id() + ".error",
            event));
    logger.error(
        "Job {} lost its worker and is failed after {} retries", job.id(), job.retryCount());
  }

  private static OffsetDateTime utc(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private record LostJob(
      String id,
      String userId,
      String featureKey,
      String payloadJson,
      String dataClass,
      int retryCount,
      String holdId,
      String model) {

    JobCommand toCommand(ObjectMapper mapper) {
      Map<String, Object> payload =
          payloadJson == null ? Map.of() : mapper.readValue(payloadJson, PAYLOAD);
      JobCommand command =
          new JobCommand(
              id,
              userId,
              featureKey,
              model == null ? "default" : model,
              payload,
              DataClass.valueOf(dataClass));
      // A metered job is re-published metered and deferred, exactly as its producer sent it.
      return holdId == null ? command : command.withReservation(holdId, id).withDeferredMetering();
    }
  }
}
