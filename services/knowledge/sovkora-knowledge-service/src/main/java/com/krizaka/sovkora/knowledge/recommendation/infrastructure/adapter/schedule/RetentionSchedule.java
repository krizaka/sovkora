package com.krizaka.sovkora.knowledge.recommendation.infrastructure.adapter.schedule;

import com.krizaka.sovkora.knowledge.recommendation.application.service.RetentionService;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the retention sweep once a day (03:17 UTC — off the hour, off the other sweeps). */
@Component
class RetentionSchedule {

  private final RetentionService retention;

  RetentionSchedule(RetentionService retention) {
    this.retention = Objects.requireNonNull(retention, "retention");
  }

  @Scheduled(cron = "0 17 3 * * *", zone = "UTC")
  void purge() {
    retention.purge();
  }
}
