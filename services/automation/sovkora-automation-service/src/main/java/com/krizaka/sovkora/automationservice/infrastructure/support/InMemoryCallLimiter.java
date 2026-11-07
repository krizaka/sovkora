package com.krizaka.sovkora.automationservice.infrastructure.support;

import com.krizaka.sovkora.automationservice.domain.port.CallLimiter;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Token buckets in memory, one per key: per instance, like the edge's (ADR-084 §6, ADR-083 slice 3
 * for the shared store). A bucket holds {@code perMinute} tokens and refills continuously; an idle
 * bucket is dropped so a stream of unknown keys cannot grow the map.
 */
@Component
class InMemoryCallLimiter implements CallLimiter {

  private static final double SECONDS_PER_MINUTE = 60.0;
  private static final long IDLE_NANOS = 10L * 60 * 1_000_000_000L;

  private final Clock clock;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  InMemoryCallLimiter(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public Decision tryAcquire(String key, int perMinute) {
    long now = clock.millis() * 1_000_000L;
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(perMinute, now));
    return bucket.take(perMinute, now);
  }

  @Override
  public void evictIdle() {
    long now = clock.millis() * 1_000_000L;
    buckets.values().removeIf(bucket -> bucket.idleSince(now) > IDLE_NANOS);
  }

  private static final class Bucket {
    private double tokens;
    private long last;

    Bucket(int capacity, long now) {
      this.tokens = capacity;
      this.last = now;
    }

    synchronized Decision take(int perMinute, long now) {
      double refill = (now - last) / 1e9 * perMinute / SECONDS_PER_MINUTE;
      tokens = Math.min(perMinute, tokens + refill);
      last = now;
      if (tokens >= 1.0) {
        tokens -= 1.0;
        return new Decision(true, 0);
      }
      double missing = 1.0 - tokens;
      long wait = (long) Math.ceil(missing * SECONDS_PER_MINUTE / perMinute);
      return new Decision(false, Math.max(1, wait));
    }

    synchronized long idleSince(long now) {
      return now - last;
    }
  }
}
