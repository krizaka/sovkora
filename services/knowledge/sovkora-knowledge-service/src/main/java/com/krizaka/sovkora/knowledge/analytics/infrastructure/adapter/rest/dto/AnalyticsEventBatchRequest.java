package com.krizaka.sovkora.knowledge.analytics.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.knowledge.analytics.domain.model.AnalyticsEvent;
import java.util.List;

/**
 * A batch of events (at most {@value #MAX}).
 *
 * @param events the events
 */
public record AnalyticsEventBatchRequest(List<AnalyticsEventRequest> events) {

  /** The largest batch: a product's outbox drains in pages of this size. */
  public static final int MAX = 500;

  /** The validated events. */
  public List<AnalyticsEvent> toEvents() {
    if (events == null || events.size() > MAX) {
      throw new IllegalArgumentException("events: 0 to " + MAX + " per batch");
    }
    return events.stream().map(AnalyticsEventRequest::toEvent).toList();
  }
}
