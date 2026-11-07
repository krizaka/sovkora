package com.krizaka.sovkora.knowledgeservice.recommendation.domain.model;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/** Test data shared by the recommendation tests: the settings of version 1, and simple vectors. */
public final class Fixtures {

  public static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

  private Fixtures() {}

  /** The values the migration seeds (version 1, 40-knowledge.sql). */
  public static RecommendationSettings settings() {
    return settings(true, 2, 0.7);
  }

  public static RecommendationSettings settings(boolean enabled, int creatorCap, double lambda) {
    Map<InteractionKind, Double> kinds = new EnumMap<>(InteractionKind.class);
    kinds.put(InteractionKind.VIEW, 1.0);
    kinds.put(InteractionKind.LIKE, 3.0);
    kinds.put(InteractionKind.SAVE, 3.0);
    kinds.put(InteractionKind.SHARE, 2.0);
    kinds.put(InteractionKind.TIP, 5.0);
    kinds.put(InteractionKind.UNLOCK, 5.0);
    kinds.put(InteractionKind.SKIP, -2.0);
    kinds.put(InteractionKind.REPORT, -5.0);
    return new RecommendationSettings(
        1,
        enabled,
        new RankingWeights(0.45, 0.2, 0.15, 0.12, 0.08),
        kinds,
        168,
        72,
        lambda,
        creatorCap,
        100,
        200,
        7,
        true,
        180,
        30,
        3);
  }

  /** A unit vector along one axis, slightly tilted toward another (so cosines are not all 0/1). */
  public static Vector axis(int dim, int tiltDim, float tilt) {
    float[] v = new float[Vector.DIMENSIONS];
    v[dim] = 1f;
    v[tiltDim] += tilt;
    return Vector.of(v);
  }

  public static Vector axis(int dim) {
    float[] v = new float[Vector.DIMENSIONS];
    v[dim] = 1f;
    return Vector.of(v);
  }
}
