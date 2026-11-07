package com.krizaka.sovkora.knowledgeservice.recommendation.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One interaction event, as the product sent it — validated once, here (parse at the edge).
 *
 * @param eventId the product's idempotency key: a second event with the same id is a duplicate
 * @param userId the product's <em>pseudonym</em> of the person (never its account id, an e-mail or
 *     a name — ADR-078 §3)
 * @param itemId the item, for every kind but {@code FOLLOW}/{@code UNFOLLOW}
 * @param creatorId the creator, required for {@code FOLLOW}/{@code UNFOLLOW}
 * @param kind what happened
 * @param watchedPct for a {@code VIEW}, how much of the item was watched (0–100), when known
 * @param occurredAt when it happened
 */
public record Interaction(
    String eventId,
    String userId,
    String itemId,
    String creatorId,
    InteractionKind kind,
    Integer watchedPct,
    Instant occurredAt) {

  public Interaction {
    eventId = Identifiers.require(eventId, "id");
    userId = Identifiers.requirePseudonym(userId);
    Objects.requireNonNull(kind, "kind is required");
    Objects.requireNonNull(occurredAt, "at is required");
    if (kind.targetsCreator()) {
      creatorId = Identifiers.require(creatorId, "creator");
      itemId = null;
    } else {
      itemId = Identifiers.require(itemId, "item");
      creatorId = creatorId == null ? null : Identifiers.require(creatorId, "creator");
    }
    if (watchedPct != null && (watchedPct < 0 || watchedPct > 100)) {
      throw new IllegalArgumentException("watchedPct must be between 0 and 100");
    }
  }
}
