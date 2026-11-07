package com.krizaka.sovkora.tools.infrastructure.research;

import com.krizaka.sovkora.tools.domain.port.RateGate;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Per-key budgets per minute, in this instance's memory: each instance spends its own share, as the
 * edge's rate limiter does until a shared store exists (ADR-083 slice 3).
 */
@Component
class InMemoryRateGate implements RateGate {

  private record Window(long minute, int used) {}

  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  @Override
  public boolean tryAcquire(String key, int perMinute, Instant now) {
    long minute = now.getEpochSecond() / 60;
    AtomicBoolean granted = new AtomicBoolean();
    windows.compute(
        key,
        (k, w) -> {
          Window current = w == null || w.minute() != minute ? new Window(minute, 0) : w;
          if (current.used() >= perMinute) {
            return current;
          }
          granted.set(true);
          return new Window(minute, current.used() + 1);
        });
    return granted.get();
  }
}
