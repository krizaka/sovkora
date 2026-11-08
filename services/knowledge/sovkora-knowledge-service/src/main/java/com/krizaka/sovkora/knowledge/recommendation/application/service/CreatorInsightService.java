package com.krizaka.sovkora.knowledge.recommendation.application.service;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.CreatorInsights;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.RecommendationSettings;
import com.krizaka.sovkora.knowledge.recommendation.domain.port.RecommendationRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Creator analytics v1 (ADR-078 §7): per item, per tag, the best hours and the audience overlap —
 * from the events the tenant sent, counts only, and nothing reported for an audience smaller than
 * the tenant's {@code minAudience}.
 */
@Service
public class CreatorInsightService {

  private static final Logger logger = LoggerFactory.getLogger(CreatorInsightService.class);

  private final RecommendationRepository repository;
  private final RecommendationSettingsService settings;

  public CreatorInsightService(
      RecommendationRepository repository, RecommendationSettingsService settings) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /** The creator's insights. */
  public CreatorInsights insights(String tenant, String creatorId) {
    RecommendationSettings s = settings.forTenant(tenant);
    CreatorInsights insights =
        repository.insights(tenant, creatorId, s.positiveKinds(), s.minAudience());
    logger.info(
        "recommendation.insights tenant={} creator={} items={}",
        tenant,
        creatorId,
        insights.items().size());
    return insights;
  }
}
