package com.krizaka.sovkora.engine.tools.infrastructure.research;

import com.krizaka.sovkora.engine.tools.domain.port.RateGate;
import java.time.Instant;

/** The production rate gate, reachable from other test packages. */
public final class TestRateGate implements RateGate {

  private final InMemoryRateGate gate = new InMemoryRateGate();

  @Override
  public boolean tryAcquire(String key, int perMinute, Instant now) {
    return gate.tryAcquire(key, perMinute, now);
  }
}
