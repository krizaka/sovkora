package com.krizaka.sovkora.knowledge.recommendation.application.service;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.IngestResult;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.Interaction;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.RecommendationSettings;
import com.krizaka.sovkora.knowledge.recommendation.domain.port.RecommendationRepository;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Interaction events in (ADR-078 §3): stored once per id (the product delivers at least once), then
 * the profiles of the people they concern are rebuilt.
 */
@Service
public class InteractionService {

  private static final Logger logger = LoggerFactory.getLogger(InteractionService.class);

  private final RecommendationRepository repository;
  private final RecommendationSettingsService settings;
  private final ProfileService profiles;
  private final Clock clock;

  public InteractionService(
      RecommendationRepository repository,
      RecommendationSettingsService settings,
      ProfileService profiles,
      Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.profiles = Objects.requireNonNull(profiles, "profiles");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Records a batch of events for the tenant. */
  public IngestResult record(String tenant, List<Interaction> events) {
    RecommendationSettings current = settings.forTenant(tenant);
    int accepted = events.isEmpty() ? 0 : repository.insertEvents(tenant, events);
    Set<String> users = new LinkedHashSet<>();
    for (Interaction event : events) {
      if (!event.kind().targetsCreator()) {
        users.add(event.userId());
      }
    }
    if (accepted > 0) {
      profiles.refresh(tenant, users, current, clock.instant());
    }
    logger.info(
        "recommendation.events tenant={} received={} accepted={} profiles={}",
        tenant,
        events.size(),
        accepted,
        accepted > 0 ? users.size() : 0);
    return new IngestResult(accepted, events.size() - accepted);
  }
}
