package com.krizaka.sovkora.job.infrastructure.adapter.schedule;

import com.krizaka.sovkora.job.application.service.JobLeaseService;
import com.krizaka.sovkora.job.application.service.LostJobRecoveryService;
import com.krizaka.sovkora.job.infrastructure.adapter.amqp.RunningJobs;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the lease of every job this worker runs alive, and takes back the jobs whose worker died
 * (krizaka-internal#669).
 *
 * <p>Both halves are on the same bean because they are one contract: the renewal is what makes
 * "expired" mean "dead", and the recovery is what an expiry is for. The renewal runs on its own
 * thread, apart from the one executing the job, so a job that blocks for twenty minutes on a model
 * call still renews.
 *
 * <p>The intervals are bootstrap wiring: how often to renew is a property of the lease it renews
 * ({@code sovkora.jobs.lease}); how often to look is {@code sovkora.jobs.lease.sweep-interval}.
 */
@Component
public class JobLeaseSweeper {

  private static final Logger logger = LoggerFactory.getLogger(JobLeaseSweeper.class);

  private final JobLeaseService jobLeaseService;
  private final LostJobRecoveryService lostJobRecoveryService;
  private final RunningJobs runningJobs;

  public JobLeaseSweeper(
      JobLeaseService jobLeaseService,
      LostJobRecoveryService lostJobRecoveryService,
      RunningJobs runningJobs) {
    this.jobLeaseService =
        Objects.requireNonNull(jobLeaseService, "JobLeaseService cannot be null");
    this.lostJobRecoveryService =
        Objects.requireNonNull(lostJobRecoveryService, "LostJobRecoveryService cannot be null");
    this.runningJobs = Objects.requireNonNull(runningJobs, "RunningJobs cannot be null");
  }

  /** Renews the lease of every job running in this process. */
  @Scheduled(fixedDelayString = "#{@jobLeasePolicy.renewInterval().toMillis()}")
  public void renew() {
    try {
      jobLeaseService.renew(runningJobs.runningIds());
    } catch (RuntimeException e) {
      // A missed beat is survivable (the lease is three beats long); a throw would stop the
      // schedule's next one.
      logger.warn("Could not renew the job leases; will retry on the next beat", e);
    }
  }

  /** Takes back the jobs whose lease expired. */
  @Scheduled(fixedDelayString = "${sovkora.jobs.lease.sweep-interval:30000}")
  public void recover() {
    lostJobRecoveryService.recoverExpired();
  }
}
