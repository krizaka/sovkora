package com.krizaka.sovkora.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JudgeCircuitBreakerTest {

  private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");

  /** A clock the test moves. */
  private static final class Moving extends Clock {
    private Instant now = T0;

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  @DisplayName("it opens after the threshold of consecutive failures and refuses calls")
  void opensAfterTheThreshold() {
    var breaker = new JudgeCircuitBreaker(2, Duration.ofSeconds(30), new Moving());

    breaker.onFailure();
    assertThat(breaker.tryAcquire()).isTrue();
    breaker.onFailure();

    assertThat(breaker.isOpen()).isTrue();
    assertThat(breaker.tryAcquire()).isFalse();
  }

  @Test
  @DisplayName("a success resets the count of consecutive failures")
  void successResets() {
    var breaker = new JudgeCircuitBreaker(2, Duration.ofSeconds(30), new Moving());

    breaker.onFailure();
    breaker.onSuccess();
    breaker.onFailure();

    assertThat(breaker.isOpen()).isFalse();
  }

  @Test
  @DisplayName(
      "after the open time one probe goes through; its success closes, its failure reopens")
  void halfOpenProbe() {
    var clock = new Moving();
    var breaker = new JudgeCircuitBreaker(1, Duration.ofSeconds(30), clock);
    breaker.onFailure();

    clock.advance(Duration.ofSeconds(31));
    assertThat(breaker.tryAcquire()).as("the probe").isTrue();
    assertThat(breaker.tryAcquire()).as("only one probe at a time").isFalse();
    breaker.onFailure();
    assertThat(breaker.tryAcquire()).as("reopened").isFalse();

    clock.advance(Duration.ofSeconds(31));
    assertThat(breaker.tryAcquire()).isTrue();
    breaker.onSuccess();
    assertThat(breaker.isOpen()).isFalse();
    assertThat(breaker.tryAcquire()).isTrue();
  }
}
