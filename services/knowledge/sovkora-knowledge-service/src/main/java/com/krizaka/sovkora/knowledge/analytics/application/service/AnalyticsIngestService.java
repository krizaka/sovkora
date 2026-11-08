package com.krizaka.sovkora.knowledge.analytics.application.service;

import com.krizaka.sovkora.knowledge.analytics.domain.model.AnalyticsEvent;
import com.krizaka.sovkora.knowledge.analytics.domain.model.ErasureResult;
import com.krizaka.sovkora.knowledge.analytics.domain.model.EventKind;
import com.krizaka.sovkora.knowledge.analytics.domain.model.IngestResult;
import com.krizaka.sovkora.knowledge.analytics.domain.model.KindSource;
import com.krizaka.sovkora.knowledge.analytics.domain.port.AnalyticsRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Product events in, and erasures (ADR-081 §2, §4): each kind must be one the tenant may send, and
 * a recommender kind is refused here — it goes to the recommender, which analytics reads back, so
 * nothing is counted twice.
 */
@Service
public class AnalyticsIngestService {

  private static final Logger logger = LoggerFactory.getLogger(AnalyticsIngestService.class);

  private final AnalyticsRepository repository;
  private final AnalyticsSettingsService settings;

  public AnalyticsIngestService(AnalyticsRepository repository, AnalyticsSettingsService settings) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /** Records a batch for the tenant, or refuses it whole when one kind is not accepted here. */
  public IngestResult record(String tenant, List<AnalyticsEvent> events) {
    Map<String, EventKind> kinds = settings.kinds(tenant);
    for (AnalyticsEvent event : events) {
      EventKind kind = kinds.get(event.kind());
      if (kind == null) {
        throw new IllegalArgumentException("unknown kind: " + event.kind());
      }
      if (kind.source() == KindSource.RECOMMENDER) {
        throw new IllegalArgumentException(
            "kind "
                + event.kind()
                + " is a recommender event: send it to /api/v1/recommendations/events");
      }
    }
    int accepted = events.isEmpty() ? 0 : repository.insertEvents(tenant, events);
    logger.info(
        "analytics.events tenant={} received={} accepted={}", tenant, events.size(), accepted);
    return new IngestResult(accepted, events.size() - accepted);
  }

  /** Forgets subjects and items in the analytics and the recommender's events. */
  public ErasureResult erase(String tenant, Set<String> subjects, Set<String> items) {
    int rows = repository.erase(tenant, subjects, items);
    logger.info(
        "analytics.erasure tenant={} subjects={} items={} events={}",
        tenant,
        subjects.size(),
        items.size(),
        rows);
    return new ErasureResult(subjects.size(), items.size(), rows);
  }
}
