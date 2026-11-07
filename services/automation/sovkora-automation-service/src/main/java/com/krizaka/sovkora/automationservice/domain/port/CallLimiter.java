package com.krizaka.sovkora.automationservice.domain.port;

/** A token bucket per key: how many calls a minute, and when to come back when it is empty. */
public interface CallLimiter {

  /**
   * Takes one token.
   *
   * @param key what is being limited
   * @param perMinute the bucket's size and refill, per minute
   * @return whether the call may go on, and when to retry when it may not
   */
  Decision tryAcquire(String key, int perMinute);

  /** Forgets the buckets that have not been touched for a while. */
  void evictIdle();

  /**
   * The answer of a limiter.
   *
   * @param allowed whether a token was taken
   * @param retryAfterSeconds whole seconds to the next token, 0 when allowed
   */
  record Decision(boolean allowed, long retryAfterSeconds) {}
}
