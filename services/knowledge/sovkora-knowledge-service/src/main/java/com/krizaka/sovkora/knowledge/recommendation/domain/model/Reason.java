package com.krizaka.sovkora.knowledge.recommendation.domain.model;

/**
 * Why an item is in a feed — the candidate source it came from. Returned with every item, so the
 * product can say "because you liked…" and an operator can see what the ranking did (ADR-078 §4).
 */
public enum Reason {
  /** Near the person's profile (approximate nearest neighbours). */
  SIMILAR,
  /** People who engaged the same items engaged this one. */
  CO_ENGAGED,
  /** New. */
  FRESH,
  /** From a creator the person follows. */
  FOLLOWED,
  /** Engaged a lot this week. */
  POPULAR
}
