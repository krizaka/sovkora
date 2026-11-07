package com.krizaka.sovkora.knowledgeservice.analytics.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.knowledgeservice.analytics.domain.model.AnalyticsEvent;
import java.time.Instant;
import java.util.Locale;

/**
 * One event as the product sends it.
 *
 * @param id idempotency key
 * @param subject the person's pseudonym
 * @param kind a kind of {@code analytics_event_kind} (lower snake case)
 * @param item the item it concerns, when any
 * @param owner the item's owner, when any
 * @param value the kind's measure, when it has one
 * @param at when it happened (ISO-8601)
 */
public record AnalyticsEventRequest(
    String id, String subject, String kind, String item, String owner, Double value, Instant at) {

  /** The validated domain event. */
  public AnalyticsEvent toEvent() {
    if (kind == null) {
      throw new IllegalArgumentException("kind is required");
    }
    return new AnalyticsEvent(
        id, subject, kind.trim().toLowerCase(Locale.ROOT), item, owner, value, at);
  }
}
