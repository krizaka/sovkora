package com.krizaka.sovkora.core.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable semantic routing rule mapping a classified intent to required interceptor beans.
 *
 * <p>When the {@code SemanticRoutingEngine} classifies a prompt with an intent matching {@code
 * intentLabel} at or above {@code confidenceThreshold}, the interceptors identified by {@code
 * requiredInterceptorKeys} are activated in Phase 2 of the dynamic pipeline.
 *
 * @param intentLabel The semantic intent label (e.g., {@code "video_generation"}, {@code
 *     "translation_required"}, {@code "strict_json_format"}).
 * @param requiredInterceptorKeys Bean class simple names of the interceptors to activate.
 * @param confidenceThreshold Minimum confidence score (0.0–1.0) required for this route to match.
 */
public record ConditionalRoute(
    String intentLabel, List<String> requiredInterceptorKeys, double confidenceThreshold) {

  /** Compact constructor enforcing non-null invariants and defensive copies. */
  public ConditionalRoute {
    Objects.requireNonNull(intentLabel, "intentLabel must not be null");
    if (intentLabel.isBlank()) {
      throw new IllegalArgumentException("intentLabel must not be blank");
    }
    requiredInterceptorKeys =
        requiredInterceptorKeys != null ? List.copyOf(requiredInterceptorKeys) : List.of();
    if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
      throw new IllegalArgumentException(
          "confidenceThreshold must be between 0.0 and 1.0, got: " + confidenceThreshold);
    }
  }

  /**
   * Tests whether a classified confidence score meets or exceeds this route's threshold.
   *
   * @param confidence The classified confidence score.
   * @return {@code true} if the confidence meets the threshold.
   */
  public boolean matches(double confidence) {
    return confidence >= confidenceThreshold;
  }
}
