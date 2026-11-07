package com.krizaka.sovkora.knowledgeservice.analytics.domain.model;

/**
 * A figure and the same figure over the previous period.
 *
 * @param current over the range asked
 * @param previous over the range of the same length just before
 * @param change the relative change ({@code 0.25} = +25 %), absent when there was nothing before
 */
public record Figure(long current, long previous, Double change) {

  /** The figure with its change computed. */
  public static Figure of(long current, long previous) {
    return new Figure(
        current, previous, previous == 0 ? null : round((current - previous) / (double) previous));
  }

  static double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }
}
