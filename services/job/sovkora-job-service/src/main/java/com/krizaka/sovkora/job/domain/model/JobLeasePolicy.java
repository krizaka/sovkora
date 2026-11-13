package com.krizaka.sovkora.job.domain.model;

import java.time.Duration;

/**
 * How long a worker's claim on a job lasts, how often it is renewed, and how many times a job whose
 * worker died is put back before it is failed (krizaka-internal#669).
 *
 * <p>Bounded on purpose: a lease shorter than a few heartbeats fails live jobs, one longer than an
 * hour leaves a person waiting; a retry cap above five re-runs paid work too often, and a negative
 * one is meaningless. An out-of-range value stops the service at boot rather than being clamped.
 *
 * @param lease how long a claim lasts without a renewal
 * @param renewInterval how often a running job's lease is renewed
 * @param maxRetries how many times a lost job is re-enqueued before it fails; {@code 0} fails at
 *     once
 */
public record JobLeasePolicy(Duration lease, Duration renewInterval, int maxRetries) {

  static final Duration MIN_LEASE = Duration.ofSeconds(30);
  static final Duration MAX_LEASE = Duration.ofHours(1);
  static final int MAX_RETRIES = 5;

  public JobLeasePolicy {
    if (lease.compareTo(MIN_LEASE) < 0 || lease.compareTo(MAX_LEASE) > 0) {
      throw new IllegalArgumentException(
          "sovkora.jobs.lease.lease-seconds must be between 30s and 1h, not " + lease);
    }
    if (renewInterval.isNegative()
        || renewInterval.isZero()
        || renewInterval.multipliedBy(3).compareTo(lease) > 0) {
      throw new IllegalArgumentException(
          "sovkora.jobs.lease.renew-interval-seconds must be positive and at most a third of the lease, not "
              + renewInterval);
    }
    if (maxRetries < 0 || maxRetries > MAX_RETRIES) {
      throw new IllegalArgumentException(
          "sovkora.jobs.lease.max-retries must be between 0 and 5, not " + maxRetries);
    }
  }
}
