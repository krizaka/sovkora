package com.krizaka.sovkora.knowledgeservice.recommendation.application.service;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RecommendationSettings;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.port.RecommendationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Retention (Loi 25, ADR-078 §3): each tenant's events older than its {@code retentionDays}, and
 * its served-request log older than {@code requestLogDays}, are deleted.
 */
@Service
public class RetentionService {

  private static final Logger logger = LoggerFactory.getLogger(RetentionService.class);

  private final RecommendationRepository repository;
  private final RecommendationSettingsService settings;
  private final Clock clock;

  public RetentionService(
      RecommendationRepository repository, RecommendationSettingsService settings, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Purges every tenant. */
  public void purge() {
    Instant now = clock.instant();
    for (String tenant : repository.tenants()) {
      RecommendationSettings s = settings.forTenant(tenant);
      int events = repository.purgeEvents(tenant, now.minus(Duration.ofDays(s.retentionDays())));
      int requests =
          repository.purgeRequests(tenant, now.minus(Duration.ofDays(s.requestLogDays())));
      logger.info(
          "recommendation.retention tenant={} events={} requests={}", tenant, events, requests);
    }
  }
}
