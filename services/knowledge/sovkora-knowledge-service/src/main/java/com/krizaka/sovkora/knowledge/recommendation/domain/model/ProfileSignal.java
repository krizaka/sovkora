package com.krizaka.sovkora.knowledge.recommendation.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One interaction as the profile sees it: what kind, when, how much was watched, and the vector of
 * the item.
 *
 * @param kind the kind
 * @param occurredAt when
 * @param watchedPct for a view, how much was watched, when known
 * @param vector the item's vector
 */
public record ProfileSignal(
    InteractionKind kind, Instant occurredAt, Integer watchedPct, Vector vector) {

  public ProfileSignal {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(vector, "vector");
  }
}
