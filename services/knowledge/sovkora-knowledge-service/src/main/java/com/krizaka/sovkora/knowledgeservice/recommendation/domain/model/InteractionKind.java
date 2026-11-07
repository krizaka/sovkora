package com.krizaka.sovkora.knowledgeservice.recommendation.domain.model;

/**
 * What a person did with an item (or, for {@link #FOLLOW} and {@link #UNFOLLOW}, with a creator).
 * The weight each kind carries in a profile is a setting ({@link
 * RecommendationSettings#kindWeights()}), never a constant here: this enum only says what a kind
 * <em>is</em>.
 */
public enum InteractionKind {
  VIEW,
  LIKE,
  SAVE,
  SHARE,
  TIP,
  UNLOCK,
  FOLLOW,
  UNFOLLOW,
  SKIP,
  REPORT;

  /** {@code true} when the event is about a creator, not an item. */
  public boolean targetsCreator() {
    return this == FOLLOW || this == UNFOLLOW;
  }
}
