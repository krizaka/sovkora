package com.krizaka.sovkora.knowledge.analytics.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A half-open time range {@code [from, to)} a question is asked over.
 *
 * @param from the first instant included
 * @param to the first instant excluded
 */
public record Range(Instant from, Instant to) {

  public Range {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("from must be before to");
    }
  }

  /** The range of the same length just before this one — what a delta compares with. */
  public Range previous() {
    Duration length = Duration.between(from, to);
    return new Range(from.minus(length), from);
  }

  /** This range, or an {@link IllegalArgumentException} when it is longer than allowed. */
  public Range requireAtMostDays(int days) {
    if (Duration.between(from, to).compareTo(Duration.ofDays(days)) > 0) {
      throw new IllegalArgumentException("the range is at most " + days + " days");
    }
    return this;
  }
}
