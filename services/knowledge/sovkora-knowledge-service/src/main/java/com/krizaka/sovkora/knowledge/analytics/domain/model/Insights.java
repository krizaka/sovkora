package com.krizaka.sovkora.knowledge.analytics.domain.model;

import java.util.List;

/**
 * What a tenant's events say over a range (ADR-081 §3) — counts and rates only, never a person.
 *
 * @param range the range asked
 * @param owner the owner the figures are narrowed to, when one was asked
 * @param minAudience the smallest audience a figure is reported for
 * @param suppressed true when the whole range is under the threshold: nothing else is reported
 * @param events events, with the previous period
 * @param activeSubjects distinct subjects, with the previous period
 * @param kinds each kind's figures (kinds under the threshold left out)
 * @param series the days of the range
 * @param topItems the most engaging items
 * @param cohorts weekly cohorts and their retention
 */
public record Insights(
    Range range,
    String owner,
    int minAudience,
    boolean suppressed,
    Figure events,
    Figure activeSubjects,
    List<KindFigure> kinds,
    List<DayPoint> series,
    List<TopItem> topItems,
    List<Cohort> cohorts) {

  public Insights {
    kinds = List.copyOf(kinds);
    series = List.copyOf(series);
    topItems = List.copyOf(topItems);
    cohorts = List.copyOf(cohorts);
  }

  /** The answer for a range whose audience is under the threshold. */
  public static Insights suppressed(Range range, String owner, int minAudience) {
    return new Insights(
        range, owner, minAudience, true, null, null, List.of(), List.of(), List.of(), List.of());
  }
}
