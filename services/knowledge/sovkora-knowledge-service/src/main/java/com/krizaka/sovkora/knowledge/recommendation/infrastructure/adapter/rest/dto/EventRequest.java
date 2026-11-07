package com.krizaka.sovkora.knowledge.recommendation.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.knowledge.recommendation.domain.model.Interaction;
import com.krizaka.sovkora.knowledge.recommendation.domain.model.InteractionKind;
import java.time.Instant;
import java.util.Locale;

/**
 * One event as the product sends it.
 *
 * @param id idempotency key
 * @param user the person's pseudonym
 * @param item the item (absent for FOLLOW/UNFOLLOW)
 * @param creator the creator (required for FOLLOW/UNFOLLOW)
 * @param kind VIEW · LIKE · SAVE · SHARE · TIP · UNLOCK · FOLLOW · UNFOLLOW · SKIP · REPORT
 * @param watchedPct for a view, the share watched (0–100)
 * @param at when it happened (ISO-8601)
 */
public record EventRequest(
    String id,
    String user,
    String item,
    String creator,
    String kind,
    Integer watchedPct,
    Instant at) {

  /** The validated domain event. */
  public Interaction toInteraction() {
    if (kind == null) {
      throw new IllegalArgumentException("kind is required");
    }
    InteractionKind parsed;
    try {
      parsed = InteractionKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("unknown kind", e);
    }
    return new Interaction(id, user, item, creator, parsed, watchedPct, at);
  }
}
