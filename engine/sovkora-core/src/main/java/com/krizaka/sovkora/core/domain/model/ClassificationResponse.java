package com.krizaka.sovkora.core.domain.model;

import java.util.List;

/**
 * Deserialization target for the LocalAI classification endpoint response.
 *
 * <p>Maps the JSON payload returned by {@code POST /v1/classify} into a typed Java record for
 * consumption by the {@code SemanticRoutingEngine}.
 *
 * <p>Example JSON:
 *
 * <pre>{@code
 * {
 *   "intents": [
 *     { "label": "video_generation", "confidence": 0.92 },
 *     { "label": "translation_required", "confidence": 0.45 }
 *   ]
 * }
 * }</pre>
 *
 * @param intents List of classified intents with confidence scores.
 */
public record ClassificationResponse(List<ClassifiedIntent> intents) {

  /** Compact constructor enforcing defensive copy. */
  public ClassificationResponse {
    intents = intents != null ? List.copyOf(intents) : List.of();
  }

  /**
   * Individual classified intent with a label and confidence score.
   *
   * @param label The semantic intent label (e.g., {@code "video_generation"}).
   * @param confidence The classification confidence score (0.0–1.0).
   */
  public record ClassifiedIntent(String label, double confidence) {}
}
