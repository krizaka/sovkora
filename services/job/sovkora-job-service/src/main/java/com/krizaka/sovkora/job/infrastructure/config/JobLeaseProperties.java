package com.krizaka.sovkora.job.infrastructure.config;

import com.krizaka.sovkora.job.domain.model.JobLeasePolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The {@code sovkora.jobs.lease} keys as written; the rules on them are {@link JobLeasePolicy}'s,
 * which refuses an out-of-range value at boot.
 *
 * @param leaseSeconds how long a claim lasts without a renewal
 * @param renewIntervalSeconds how often a running job's lease is renewed
 * @param maxRetries how many times a lost job is re-enqueued before it fails
 */
@ConfigurationProperties(prefix = "sovkora.jobs.lease")
public record JobLeaseProperties(
    @DefaultValue("120") long leaseSeconds,
    @DefaultValue("30") long renewIntervalSeconds,
    @DefaultValue("2") int maxRetries) {

  /**
   * @return the validated policy
   */
  public JobLeasePolicy toPolicy() {
    return new JobLeasePolicy(
        Duration.ofSeconds(leaseSeconds), Duration.ofSeconds(renewIntervalSeconds), maxRetries);
  }
}
