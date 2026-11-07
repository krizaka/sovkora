package com.krizaka.sovkora.knowledgeservice.recommendation.infrastructure.adapter.persistence;

import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.InteractionKind;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RankingWeights;
import com.krizaka.sovkora.knowledgeservice.recommendation.domain.model.RecommendationSettings;
import java.util.EnumMap;
import java.util.Map;

/**
 * The JSON shape of a {@code reco_settings.settings} document — read at the store's edge and turned
 * into the bounded {@link RecommendationSettings} (a document outside the bounds fails loudly).
 */
record SettingsDocument(
    Boolean enabled,
    Weights weights,
    Map<String, Double> kindWeights,
    Double halfLifeHours,
    Double freshHalfLifeHours,
    Double mmrLambda,
    Integer creatorCap,
    Integer candidatePool,
    Integer profileEvents,
    Integer popularWindowDays,
    Boolean excludeSeen,
    Integer retentionDays,
    Integer requestLogDays,
    Integer minAudience) {

  record Weights(
      Double similarity,
      Double coEngagement,
      Double freshness,
      Double followed,
      Double popularity) {}

  RecommendationSettings toSettings(int version) {
    if (weights == null || kindWeights == null) {
      throw new IllegalStateException("reco_settings v" + version + ": weights are required");
    }
    Map<InteractionKind, Double> kinds = new EnumMap<>(InteractionKind.class);
    kindWeights.forEach((k, w) -> kinds.put(InteractionKind.valueOf(k), w));
    return new RecommendationSettings(
        version,
        Boolean.TRUE.equals(enabled),
        new RankingWeights(
            required(weights.similarity(), "weights.similarity"),
            required(weights.coEngagement(), "weights.coEngagement"),
            required(weights.freshness(), "weights.freshness"),
            required(weights.followed(), "weights.followed"),
            required(weights.popularity(), "weights.popularity")),
        kinds,
        required(halfLifeHours, "halfLifeHours"),
        required(freshHalfLifeHours, "freshHalfLifeHours"),
        required(mmrLambda, "mmrLambda"),
        required(creatorCap, "creatorCap"),
        required(candidatePool, "candidatePool"),
        required(profileEvents, "profileEvents"),
        required(popularWindowDays, "popularWindowDays"),
        !Boolean.FALSE.equals(excludeSeen),
        required(retentionDays, "retentionDays"),
        required(requestLogDays, "requestLogDays"),
        required(minAudience, "minAudience"));
  }

  private static <T> T required(T value, String name) {
    if (value == null) {
      throw new IllegalStateException("reco_settings: " + name + " is required");
    }
    return value;
  }
}
