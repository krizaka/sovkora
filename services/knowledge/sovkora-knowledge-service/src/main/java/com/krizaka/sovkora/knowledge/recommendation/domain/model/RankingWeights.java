package com.krizaka.sovkora.knowledge.recommendation.domain.model;

/**
 * The weights of the five ranking terms (ADR-078 §4). Each in [0, 1]; they need not sum to 1 — the
 * score is relative.
 *
 * @param similarity closeness to the profile
 * @param coEngagement peers engaged it
 * @param freshness new
 * @param followed from a followed creator
 * @param popularity engaged a lot this week
 */
public record RankingWeights(
    double similarity, double coEngagement, double freshness, double followed, double popularity) {

  public RankingWeights {
    for (double w : new double[] {similarity, coEngagement, freshness, followed, popularity}) {
      if (!(w >= 0 && w <= 1)) {
        throw new IllegalArgumentException("a ranking weight is between 0 and 1");
      }
    }
  }
}
