package com.krizaka.sovkora.engine.core.application.routing;

/**
 * Provider metrics — real-time health and performance data for deterministic routing.
 *
 * @param healthScore Resilience4j circuit-breaker health score (0.0–1.0).
 * @param p95LatencyMs 95th percentile latency in milliseconds.
 * @param providerVector Embedding vector for intent similarity scoring.
 */
public record ProviderMetrics(double healthScore, double p95LatencyMs, float[] providerVector) {

  public ProviderMetrics {
    if (healthScore < 0.0 || healthScore > 1.0) {
      throw new IllegalArgumentException("healthScore must be in [0.0, 1.0], got: " + healthScore);
    }
    if (p95LatencyMs < 0.0) {
      throw new IllegalArgumentException("p95LatencyMs must be non-negative, got: " + p95LatencyMs);
    }
    if (providerVector == null || providerVector.length == 0) {
      throw new IllegalArgumentException("providerVector must be non-null and non-empty");
    }
    providerVector = providerVector.clone();
  }

  @Override
  public float[] providerVector() {
    return providerVector.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ProviderMetrics(var hs, var lat, var vec)
        && Double.compare(healthScore, hs) == 0
        && Double.compare(p95LatencyMs, lat) == 0
        && java.util.Arrays.equals(providerVector, vec);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        healthScore, p95LatencyMs, java.util.Arrays.hashCode(providerVector));
  }

  @Override
  public String toString() {
    return "ProviderMetrics[healthScore="
        + healthScore
        + ", p95LatencyMs="
        + p95LatencyMs
        + ", providerVector="
        + java.util.Arrays.toString(providerVector)
        + "]";
  }
}
