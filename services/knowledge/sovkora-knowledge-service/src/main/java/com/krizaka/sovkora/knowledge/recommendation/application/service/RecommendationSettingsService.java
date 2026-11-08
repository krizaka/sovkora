package com.krizaka.sovkora.knowledge.recommendation.application.service;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.RecommendationSettings;
import com.krizaka.sovkora.knowledge.recommendation.domain.port.RecommendationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * The recommender's parameters per tenant, read from {@code reco_settings} (ADR-078 §5) and cached
 * for a minute — a published version reaches every request within that minute, without a redeploy.
 */
@Service
public class RecommendationSettingsService {

  /** How long a read version is reused: a technical cache, not a business parameter. */
  private static final Duration CACHE_TTL = Duration.ofMinutes(1);

  private final RecommendationRepository repository;
  private final Clock clock;
  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  public RecommendationSettingsService(RecommendationRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * The tenant's settings.
   *
   * @throws IllegalStateException when no version exists at all (the migration was not applied)
   */
  public RecommendationSettings forTenant(String tenant) {
    Instant now = clock.instant();
    Cached hit = cache.get(tenant);
    if (hit != null && hit.until().isAfter(now)) {
      return hit.settings();
    }
    RecommendationSettings settings =
        repository
            .settings(tenant)
            .orElseThrow(
                () -> new IllegalStateException("no reco_settings row: apply the migration"));
    cache.put(tenant, new Cached(settings, now.plus(CACHE_TTL)));
    return settings;
  }

  private record Cached(RecommendationSettings settings, Instant until) {}
}
