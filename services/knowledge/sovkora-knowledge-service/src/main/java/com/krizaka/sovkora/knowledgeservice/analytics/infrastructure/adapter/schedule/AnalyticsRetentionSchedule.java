package com.krizaka.sovkora.knowledgeservice.analytics.infrastructure.adapter.schedule;

import com.krizaka.sovkora.knowledgeservice.analytics.application.service.AnalyticsRetentionService;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the analytics retention sweep once a day (03:27 UTC — after the recommender's). */
@Component
class AnalyticsRetentionSchedule {

  private final AnalyticsRetentionService retention;

  AnalyticsRetentionSchedule(AnalyticsRetentionService retention) {
    this.retention = Objects.requireNonNull(retention, "retention");
  }

  @Scheduled(cron = "0 27 3 * * *", zone = "UTC")
  void purge() {
    retention.purge();
  }
}
