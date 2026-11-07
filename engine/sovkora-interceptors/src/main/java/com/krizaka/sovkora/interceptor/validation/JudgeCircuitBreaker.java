package com.krizaka.sovkora.interceptor.validation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Circuit breaker of the semantic judge (Tier C / Tier D, sovkora-engine#125). After {@code
 * failureThreshold} consecutive failures it opens and refuses calls for {@code openFor}; then one
 * probe is let through (half-open): its success closes the breaker, its failure opens it again.
 * While it is open the judge is not called at all — the policy decides what the turn does (admin
 * data), never a silent pass.
 */
final class JudgeCircuitBreaker {

  private final int failureThreshold;
  private final Duration openFor;
  private final Clock clock;

  private int consecutiveFailures;
  private Instant openedAt;
  private boolean probing;

  JudgeCircuitBreaker(int failureThreshold, Duration openFor, Clock clock) {
    this.failureThreshold = Math.max(1, failureThreshold);
    this.openFor = Objects.requireNonNull(openFor, "openFor");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * @return whether a call may go to the judge now (closed, or the one half-open probe)
   */
  synchronized boolean tryAcquire() {
    if (openedAt == null) {
      return true;
    }
    if (!probing && !clock.instant().isBefore(openedAt.plus(openFor))) {
      probing = true;
      return true;
    }
    return false;
  }

  /** The judge answered. */
  synchronized void onSuccess() {
    consecutiveFailures = 0;
    openedAt = null;
    probing = false;
  }

  /** The judge timed out, failed or answered nothing usable. */
  synchronized void onFailure() {
    probing = false;
    consecutiveFailures++;
    if (consecutiveFailures >= failureThreshold) {
      openedAt = clock.instant();
    }
  }

  /**
   * @return whether the breaker is open (calls are refused until the probe)
   */
  synchronized boolean isOpen() {
    return openedAt != null;
  }
}
