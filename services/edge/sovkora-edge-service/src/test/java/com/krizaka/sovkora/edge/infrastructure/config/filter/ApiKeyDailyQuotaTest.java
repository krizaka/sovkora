package com.krizaka.sovkora.edge.infrastructure.config.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiKeyDailyQuotaTest {

  private static final class MutableClock extends Clock {
    private volatile Instant now = Instant.parse("2026-10-20T12:00:00Z");

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

  private final MutableClock clock = new MutableClock();

  @Test
  @DisplayName("A key is refused once its day's quota is spent, other keys are not")
  void refusesPastTheLimit() {
    ApiKeyDailyQuota quota = new ApiKeyDailyQuota(2, clock);
    assertThat(quota.tryAcquire("oz_a", null)).isTrue();
    assertThat(quota.tryAcquire("oz_a", null)).isTrue();
    assertThat(quota.tryAcquire("oz_a", null)).isFalse();
    assertThat(quota.tryAcquire("oz_b", null)).isTrue();
  }

  @Test
  @DisplayName("The counters start again at 00:00 UTC")
  void resetsAtMidnight() {
    ApiKeyDailyQuota quota = new ApiKeyDailyQuota(1, clock);
    assertThat(quota.tryAcquire("oz_a", null)).isTrue();
    assertThat(quota.tryAcquire("oz_a", null)).isFalse();
    clock.now = Instant.parse("2026-10-21T00:00:01Z");
    assertThat(quota.tryAcquire("oz_a", null)).isTrue();
  }

  @Test
  @DisplayName("Concurrent callers never get more than the limit, also across the midnight swap")
  void concurrentCallersRespectTheLimit() throws Exception {
    ApiKeyDailyQuota quota = new ApiKeyDailyQuota(100, clock);
    ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    AtomicInteger granted = new AtomicInteger();
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < 400; i++) {
      futures.add(
          pool.submit(
              () -> {
                if (quota.tryAcquire("oz_race", null)) {
                  granted.incrementAndGet();
                }
              }));
    }
    for (Future<?> f : futures) {
      f.get();
    }
    pool.shutdown();
    assertThat(granted.get()).isEqualTo(100);
    assertThat(quota.secondsUntilReset()).isEqualTo(12 * 3600);
  }

  @Test
  @DisplayName("Two owners with different limits are each refused at their own")
  void eachOwnerIsRefusedAtItsOwnLimit() {
    ApiKeyDailyQuota quota = new ApiKeyDailyQuota(100, clock);
    assertThat(quota.tryAcquire("oz_small", 1)).isTrue();
    assertThat(quota.tryAcquire("oz_small", 1)).isFalse();
    assertThat(quota.tryAcquire("oz_big", 3)).isTrue();
    assertThat(quota.tryAcquire("oz_big", 3)).isTrue();
    assertThat(quota.tryAcquire("oz_big", 3)).isTrue();
    assertThat(quota.tryAcquire("oz_big", 3)).isFalse();
  }

  @Test
  @DisplayName("An owner without a limit of its own falls back to the global setting")
  void nullFallsBackToTheGlobalDefault() {
    ApiKeyDailyQuota quota = new ApiKeyDailyQuota(1, clock);
    assertThat(quota.tryAcquire("oz_a", null)).isTrue();
    assertThat(quota.tryAcquire("oz_a", null)).isFalse();
  }

  @Test
  @DisplayName("A limit changed between two calls applies to the second one")
  void aChangedLimitAppliesOnTheNextCall() {
    ApiKeyDailyQuota quota = new ApiKeyDailyQuota(100, clock);
    assertThat(quota.tryAcquire("oz_a", 1)).isTrue();
    assertThat(quota.tryAcquire("oz_a", 1)).isFalse();
    assertThat(quota.tryAcquire("oz_a", 3)).isTrue();
    assertThat(quota.tryAcquire("oz_a", 3)).isFalse();
  }

  @Test
  @DisplayName("An owner's own limit holds even when the global quota is switched off")
  void ownLimitHoldsWhenGlobalIsOff() {
    ApiKeyDailyQuota quota = ApiKeyDailyQuota.unlimited();
    assertThat(quota.tryAcquire("oz_a", 1)).isTrue();
    assertThat(quota.tryAcquire("oz_a", 1)).isFalse();
  }
}
