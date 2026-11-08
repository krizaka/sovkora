package com.krizaka.sovkora.knowledge.analytics.domain.model;

import java.util.List;

/**
 * The analytics parameters of a tenant — one version of {@code analytics_settings} (ADR-081 §5),
 * bounded here so a document outside the bounds fails loudly instead of answering nonsense.
 *
 * @param version the settings version
 * @param retentionDays events older than this are purged
 * @param minAudience no figure is reported for fewer distinct subjects (k-anonymity)
 * @param maxRangeDays the longest range a query may ask for
 * @param cohortWeeks how many weekly cohorts, and weeks after each, are reported
 * @param funnelWindowHours how long after a funnel step the next one still counts
 * @param topItems how many top items are reported
 * @param positiveKinds the kinds that count as engagement for top items
 */
public record AnalyticsSettings(
    int version,
    int retentionDays,
    int minAudience,
    int maxRangeDays,
    int cohortWeeks,
    int funnelWindowHours,
    int topItems,
    List<String> positiveKinds) {

  public AnalyticsSettings {
    bounded(retentionDays, 1, 3650, "retentionDays");
    bounded(minAudience, 2, 1000, "minAudience");
    bounded(maxRangeDays, 1, 3650, "maxRangeDays");
    bounded(cohortWeeks, 1, 52, "cohortWeeks");
    bounded(funnelWindowHours, 1, 24 * 90, "funnelWindowHours");
    bounded(topItems, 1, 100, "topItems");
    positiveKinds = List.copyOf(positiveKinds);
    positiveKinds.forEach(EventKind::requireName);
  }

  private static void bounded(int value, int min, int max, String name) {
    if (value < min || value > max) {
      throw new IllegalStateException(
          "analytics_settings: " + name + " must be between " + min + " and " + max);
    }
  }
}
