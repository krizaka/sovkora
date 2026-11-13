package com.krizaka.sovkora.job.application.service;

import com.krizaka.sovkora.job.domain.model.JobLeasePolicy;
import com.krizaka.sovkora.jobs.api.domain.model.JobCommand;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The claim a worker holds on the job it is running, and its renewal (krizaka-internal#669).
 *
 * <p>The claim carries the worker's id and an expiry. While the worker lives it renews the expiry
 * on a heartbeat; when it dies the expiry passes and {@link LostJobRecoveryService} takes the job.
 * A renewal only touches a job this worker still holds, so a worker that was paused, declared dead
 * and replaced cannot revive its old claim on a job another worker now runs.
 *
 * <p>The claim also records what putting the job back needs and the row does not carry: the credit
 * hold and the resolved model. The job's payload is not copied: the row already has it.
 *
 * <p>Time is the injected {@link Clock}, never the database's {@code now()}, so a test moves it.
 */
@Service
public class JobLeaseService {

  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;
  private final JobLeasePolicy policy;
  private final String workerId;

  /**
   * @param jdbcTemplate over the job service's own database
   * @param clock the one clock the lease and its sweeper read
   * @param policy the lease length
   * @param configuredWorkerId this worker's id, or blank for a fresh one per process
   */
  public JobLeaseService(
      JdbcTemplate jdbcTemplate,
      Clock clock,
      JobLeasePolicy policy,
      @Value("${sovkora.jobs.worker-id:}") String configuredWorkerId) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    this.policy = Objects.requireNonNull(policy, "JobLeasePolicy cannot be null");
    this.workerId =
        configuredWorkerId == null || configuredWorkerId.isBlank()
            ? "job-service-" + UUID.randomUUID()
            : configuredWorkerId;
  }

  /**
   * Takes the lease on a job this worker has just started.
   *
   * @param command the job being run
   */
  @Transactional
  public void claim(JobCommand command) {
    jdbcTemplate.update(
        "UPDATE orazaka_jobs SET locked_by_worker = ?, lease_expires_at = ?, hold_id = ?,"
            + " model = ? WHERE id = ? AND status = 'PROCESSING'",
        workerId,
        expiry(),
        command.holdId(),
        command.model(),
        command.jobId());
  }

  /**
   * Renews the lease on every job this worker is running.
   *
   * @param jobIds the jobs running here
   * @return how many leases were renewed; a job this worker no longer holds is not one
   */
  @Transactional
  public int renew(Collection<String> jobIds) {
    OffsetDateTime expiry = expiry();
    int renewed = 0;
    for (String jobId : jobIds) {
      renewed +=
          jdbcTemplate.update(
              "UPDATE orazaka_jobs SET lease_expires_at = ? WHERE id = ?"
                  + " AND status = 'PROCESSING' AND locked_by_worker = ?",
              expiry,
              jobId,
              workerId);
    }
    return renewed;
  }

  private OffsetDateTime expiry() {
    return OffsetDateTime.ofInstant(clock.instant().plus(policy.lease()), ZoneOffset.UTC);
  }
}
