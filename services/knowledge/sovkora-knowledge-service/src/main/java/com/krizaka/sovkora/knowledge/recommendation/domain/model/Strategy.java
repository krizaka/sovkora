package com.krizaka.sovkora.knowledge.recommendation.domain.model;

/** How a feed was made. */
public enum Strategy {
  /** From the person's own signals (profile, follows, co-engagement). */
  PERSONAL,
  /** The person has no signal yet: fresh and popular, through the same re-ranker. */
  COLD_START,
  /** Items near another item. */
  SIMILAR
}
