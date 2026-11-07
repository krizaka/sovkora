package com.krizaka.sovkora.automationservice.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock a test moves by hand. */
public final class FixedClock extends Clock {

  private Instant now;

  /**
   * @param start the first instant
   */
  public FixedClock(Instant start) {
    this.now = start;
  }

  /**
   * Moves the clock forward.
   *
   * @param by how far
   */
  public void advance(Duration by) {
    now = now.plus(by);
  }

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
