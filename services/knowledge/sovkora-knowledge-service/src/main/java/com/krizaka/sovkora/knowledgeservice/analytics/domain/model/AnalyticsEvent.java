package com.krizaka.sovkora.knowledgeservice.analytics.domain.model;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.Identifiers;
import java.time.Instant;
import java.util.Objects;

/**
 * One product event, as the product sent it — validated once, here (ADR-081 §2).
 *
 * @param eventId the product's idempotency key
 * @param subjectId the product's <em>pseudonym</em> of the person (never an account id, an e-mail
 *     or a name — ADR-081 §4)
 * @param kind what happened, a kind of {@code analytics_event_kind}
 * @param itemId the item it concerns, when it concerns one
 * @param ownerId the owner of that item (a creator), when there is one
 * @param value the kind's measure (seconds, cents, percent), when it has one
 * @param occurredAt when it happened
 */
public record AnalyticsEvent(
    String eventId,
    String subjectId,
    String kind,
    String itemId,
    String ownerId,
    Double value,
    Instant occurredAt) {

  public AnalyticsEvent {
    eventId = Identifiers.require(eventId, "id");
    subjectId = Identifiers.requirePseudonym(subjectId);
    kind = EventKind.requireName(kind);
    itemId = itemId == null ? null : Identifiers.require(itemId, "item");
    ownerId = ownerId == null ? null : Identifiers.require(ownerId, "owner");
    if (value != null && (value.isNaN() || value.isInfinite() || value < 0)) {
      throw new IllegalArgumentException("value must be a finite number, 0 or more");
    }
    Objects.requireNonNull(occurredAt, "at is required");
  }
}
