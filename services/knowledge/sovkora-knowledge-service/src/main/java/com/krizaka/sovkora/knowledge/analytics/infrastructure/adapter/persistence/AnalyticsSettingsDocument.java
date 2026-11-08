package com.krizaka.sovkora.knowledge.analytics.infrastructure.adapter.persistence;

import com.krizaka.sovkora.knowledge.analytics.domain.model.AnalyticsSettings;
import java.util.List;

/**
 * The JSON shape of an {@code analytics_settings.settings} document — read at the store's edge and
 * turned into the bounded {@link AnalyticsSettings} (a document outside the bounds fails loudly).
 */
record AnalyticsSettingsDocument(
    Integer retentionDays,
    Integer minAudience,
    Integer maxRangeDays,
    Integer cohortWeeks,
    Integer funnelWindowHours,
    Integer topItems,
    List<String> positiveKinds) {

  AnalyticsSettings toSettings(int version) {
    return new AnalyticsSettings(
        version,
        required(retentionDays, "retentionDays"),
        required(minAudience, "minAudience"),
        required(maxRangeDays, "maxRangeDays"),
        required(cohortWeeks, "cohortWeeks"),
        required(funnelWindowHours, "funnelWindowHours"),
        required(topItems, "topItems"),
        required(positiveKinds, "positiveKinds"));
  }

  private static <T> T required(T value, String name) {
    if (value == null) {
      throw new IllegalStateException("analytics_settings: " + name + " is required");
    }
    return value;
  }
}
