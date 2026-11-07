package com.krizaka.sovkora.core.domain.ports.outbound;

/**
 * Outbound port (ADR-094 §4.6, #172): how many answers a structured-output call may ask for before
 * it fails with a typed error (the first plus the corrections that name the schema violation). The
 * number is data an administrator edits per <b>feature key</b> ({@code
 * orazaka_feature_structured_bounds}), never a constant a deploy has to change.
 */
@FunctionalInterface
public interface StructuredOutputBounds {

  /** The bound when nothing is configured. */
  int DEFAULT_MAX_ATTEMPTS = 2; // @fixed: the safe default when no row is readable

  /**
   * @param featureKey the call's feature, or {@code null} for a call with none (the default row)
   * @return the most answers one call may ask for, at least 1
   */
  int maxAttempts(String featureKey);

  /** A bound that never changes. */
  static StructuredOutputBounds fixed(int maxAttempts) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1");
    }
    return featureKey -> maxAttempts;
  }
}
