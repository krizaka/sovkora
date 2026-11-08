package com.krizaka.sovkora.knowledge.recommendation.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.Interaction;
import java.util.List;

/**
 * A batch of events (at most {@value #MAX}).
 *
 * @param events the events
 */
public record EventBatchRequest(List<EventRequest> events) {

  /** The largest batch: a product's outbox drains in pages of this size. */
  public static final int MAX = 500;

  /** The validated events. */
  public List<Interaction> toInteractions() {
    if (events == null || events.size() > MAX) {
      throw new IllegalArgumentException("events: 0 to " + MAX + " per batch");
    }
    return events.stream().map(EventRequest::toInteraction).toList();
  }
}
