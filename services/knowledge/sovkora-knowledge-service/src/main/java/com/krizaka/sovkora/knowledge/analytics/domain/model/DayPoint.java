package com.krizaka.sovkora.knowledge.analytics.domain.model;

import java.time.LocalDate;
import java.util.Map;

/**
 * One day of the series (UTC). A day with fewer active subjects than the threshold carries no
 * figure.
 *
 * @param day the day
 * @param suppressed true when the day is under the threshold
 * @param activeSubjects distinct subjects that day, absent when suppressed
 * @param events events per kind that day, empty when suppressed
 */
public record DayPoint(
    LocalDate day, boolean suppressed, Long activeSubjects, Map<String, Long> events) {

  public DayPoint {
    events = Map.copyOf(events);
  }

  /** The day, or its suppressed form when fewer than {@code minAudience} subjects were active. */
  public static DayPoint of(
      LocalDate day, long activeSubjects, Map<String, Long> events, int minAudience) {
    return activeSubjects < minAudience
        ? new DayPoint(day, true, null, Map.of())
        : new DayPoint(day, false, activeSubjects, events);
  }
}
