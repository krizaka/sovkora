package com.krizaka.sovkora.knowledge.analytics.application.service;

import com.krizaka.sovkora.knowledge.analytics.domain.model.AnalyticsSettings;
import com.krizaka.sovkora.knowledge.analytics.domain.model.EventKind;
import com.krizaka.sovkora.knowledge.analytics.domain.port.AnalyticsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * The analytics parameters and kinds per tenant, read from {@code analytics_settings} and {@code
 * analytics_event_kind} (ADR-081 §2, §5) and cached for a minute — a published version or a new
 * kind reaches every request within that minute, without a redeploy.
 */
@Service
public class AnalyticsSettingsService {

  /** How long a read is reused: a technical cache, not a business parameter. */
  private static final Duration CACHE_TTL = Duration.ofMinutes(1);

  private final AnalyticsRepository repository;
  private final Clock clock;
  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  public AnalyticsSettingsService(AnalyticsRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * The tenant's settings.
   *
   * @throws IllegalStateException when no version exists at all (the migration was not applied)
   */
  public AnalyticsSettings forTenant(String tenant) {
    return read(tenant).settings();
  }

  /** The kinds the tenant may send, by name. */
  public Map<String, EventKind> kinds(String tenant) {
    return read(tenant).kinds();
  }

  private Cached read(String tenant) {
    Instant now = clock.instant();
    Cached hit = cache.get(tenant);
    if (hit != null && hit.until().isAfter(now)) {
      return hit;
    }
    AnalyticsSettings settings =
        repository
            .settings(tenant)
            .orElseThrow(
                () -> new IllegalStateException("no analytics_settings row: apply the migration"));
    Cached fresh = new Cached(settings, Map.copyOf(repository.kinds(tenant)), now.plus(CACHE_TTL));
    cache.put(tenant, fresh);
    return fresh;
  }

  private record Cached(AnalyticsSettings settings, Map<String, EventKind> kinds, Instant until) {}
}
