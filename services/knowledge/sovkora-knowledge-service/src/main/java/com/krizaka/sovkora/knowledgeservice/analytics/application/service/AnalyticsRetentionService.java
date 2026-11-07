package com.krizaka.sovkora.knowledgeservice.analytics.application.service;

import com.krizaka.sovkora.knowledgeservice.analytics.domain.port.AnalyticsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Retention (Loi 25, ADR-081 §4): each tenant's analytics events older than its {@code
 * retentionDays} are deleted. The recommender's events keep their own retention (ADR-078 §3).
 */
@Service
public class AnalyticsRetentionService {

  private static final Logger logger = LoggerFactory.getLogger(AnalyticsRetentionService.class);

  private final AnalyticsRepository repository;
  private final AnalyticsSettingsService settings;
  private final Clock clock;

  public AnalyticsRetentionService(
      AnalyticsRepository repository, AnalyticsSettingsService settings, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Purges every tenant. */
  public void purge() {
    Instant now = clock.instant();
    for (String tenant : repository.tenants()) {
      int days = settings.forTenant(tenant).retentionDays();
      int events = repository.purge(tenant, now.minus(Duration.ofDays(days)));
      logger.info("analytics.retention tenant={} events={}", tenant, events);
    }
  }
}
