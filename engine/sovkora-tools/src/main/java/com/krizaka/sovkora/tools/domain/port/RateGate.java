package com.krizaka.sovkora.tools.domain.port;

import java.time.Instant;

/** A per-key fetch budget per minute (one instance's share: ADR-087 §4). */
public interface RateGate {

  /**
   * Takes one unit of the key's budget for the minute.
   *
   * @param key {@code source:<key>} or {@code host:<host>}
   * @param perMinute the budget
   * @param now the instant of the request
   * @return true when a unit was available
   */
  boolean tryAcquire(String key, int perMinute, Instant now);
}
