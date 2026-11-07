package com.krizaka.sovkora.automationservice.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.automationservice.domain.port.CallLimiter.Decision;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryCallLimiterTest {

  /** A clock the test moves. */
  private static final class Moving extends Clock {
    private Instant now = Instant.parse("2026-10-20T12:00:00Z");

    void advance(Duration by) {
      now = now.plus(by);
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

  private final Moving clock = new Moving();
  private final InMemoryCallLimiter limiter = new InMemoryCallLimiter(clock);

  @Test
  @DisplayName("a bucket gives its capacity, then refuses and says when to come back")
  void exhausts() {
    for (int i = 0; i < 3; i++) {
      assertThat(limiter.tryAcquire("a", 3).allowed()).isTrue();
    }
    Decision refused = limiter.tryAcquire("a", 3);
    assertThat(refused.allowed()).isFalse();
    assertThat(refused.retryAfterSeconds()).isBetween(1L, 20L);
  }

  @Test
  @DisplayName("tokens come back with time, and keys do not share a bucket")
  void refillsAndIsolates() {
    for (int i = 0; i < 60; i++) {
      limiter.tryAcquire("a", 60);
    }
    assertThat(limiter.tryAcquire("a", 60).allowed()).isFalse();
    assertThat(limiter.tryAcquire("b", 60).allowed()).isTrue();
    clock.advance(Duration.ofSeconds(2));
    assertThat(limiter.tryAcquire("a", 60).allowed()).isTrue();
  }

  @Test
  @DisplayName("an idle bucket is forgotten, so unknown keys cannot grow the map")
  void evicts() {
    limiter.tryAcquire("idle", 1);
    assertThat(limiter.tryAcquire("idle", 1).allowed()).isFalse();
    clock.advance(Duration.ofMinutes(11));
    limiter.evictIdle();
    assertThat(limiter.tryAcquire("idle", 1).allowed()).as("a fresh, full bucket").isTrue();
  }
}
