package com.krizaka.sovkora.knowledgeservice.analytics.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * The subjects first seen in one ISO week, and the share of them active in each following week.
 *
 * @param week the Monday of the week (UTC)
 * @param size subjects first seen that week
 * @param retention week 0 (always 1.0), week 1, … — as far as the data reaches
 */
public record Cohort(LocalDate week, long size, List<Double> retention) {

  public Cohort {
    retention = List.copyOf(retention);
  }

  /** The cohort from its weekly active counts (index = weeks after the first). */
  public static Cohort of(LocalDate week, long size, List<Long> activeByWeek) {
    return new Cohort(
        week,
        size,
        activeByWeek.stream().map(n -> size == 0 ? 0.0 : Figure.round(n / (double) size)).toList());
  }
}
