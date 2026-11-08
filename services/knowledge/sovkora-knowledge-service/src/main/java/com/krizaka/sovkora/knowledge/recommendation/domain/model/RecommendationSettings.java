package com.krizaka.sovkora.knowledge.recommendation.domain.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The recommender's parameters for one tenant — data, not code (ADR-078 §5): a versioned row of
 * {@code reco_settings}, version 1 seeded by the migration. The bounds below are the safety rails a
 * published version must stay inside; the values themselves are never written in Java.
 *
 * @param version the settings version that produced a feed
 * @param enabled the tenant's switch: off answers 503 and the product falls back
 * @param weights the weights of the five ranking terms
 * @param kindWeights the weight of each interaction kind in a profile (negative for SKIP/REPORT)
 * @param halfLifeHours an interaction's weight halves every this many hours
 * @param freshHalfLifeHours an item's freshness halves every this many hours
 * @param mmrLambda MMR trade-off: 1 = relevance only, 0 = diversity only
 * @param creatorCap at most this many items of one creator per page
 * @param candidatePool how many candidates each retrieval source brings
 * @param profileEvents how many recent interactions make a profile
 * @param popularWindowDays the window of "popular"
 * @param excludeSeen leave out the items the person already engaged
 * @param retentionDays events older than this are purged (Loi 25)
 * @param requestLogDays served-request log retention
 * @param minAudience the smallest audience creator analytics reports on (k-anonymity)
 */
public record RecommendationSettings(
    int version,
    boolean enabled,
    RankingWeights weights,
    Map<InteractionKind, Double> kindWeights,
    double halfLifeHours,
    double freshHalfLifeHours,
    double mmrLambda,
    int creatorCap,
    int candidatePool,
    int profileEvents,
    int popularWindowDays,
    boolean excludeSeen,
    int retentionDays,
    int requestLogDays,
    int minAudience) {

  public RecommendationSettings {
    if (version < 1) {
      throw new IllegalArgumentException("version starts at 1");
    }
    Objects.requireNonNull(weights, "weights");
    Objects.requireNonNull(kindWeights, "kindWeights");
    EnumMap<InteractionKind, Double> kinds = new EnumMap<>(InteractionKind.class);
    for (InteractionKind kind : InteractionKind.values()) {
      double w = kindWeights.getOrDefault(kind, 0.0);
      bounded("kindWeights." + kind, w, -10, 10);
      kinds.put(kind, w);
    }
    kindWeights = Map.copyOf(kinds);
    bounded("halfLifeHours", halfLifeHours, 1, HOURS_PER_YEAR);
    bounded("freshHalfLifeHours", freshHalfLifeHours, 1, HOURS_PER_YEAR);
    bounded("mmrLambda", mmrLambda, 0, 1);
    bounded("creatorCap", creatorCap, 1, 50);
    bounded("candidatePool", candidatePool, 10, 1000);
    bounded("profileEvents", profileEvents, 1, 5000);
    bounded("popularWindowDays", popularWindowDays, 1, 90);
    bounded("retentionDays", retentionDays, 1, 730);
    bounded("requestLogDays", requestLogDays, 1, 365);
    bounded("minAudience", minAudience, 1, 1000);
  }

  /** The weight of a kind. */
  public double weightOf(InteractionKind kind) {
    return kindWeights.get(kind);
  }

  /** The kinds that count as a positive engagement (weight above zero, item kinds only). */
  public java.util.List<InteractionKind> positiveKinds() {
    return java.util.Arrays.stream(InteractionKind.values())
        .filter(k -> !k.targetsCreator() && weightOf(k) > 0)
        .toList();
  }

  private static final double HOURS_PER_YEAR = 24.0 * 365;

  private static void bounded(String name, double value, double min, double max) {
    if (!(value >= min && value <= max)) {
      throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
    }
  }
}
