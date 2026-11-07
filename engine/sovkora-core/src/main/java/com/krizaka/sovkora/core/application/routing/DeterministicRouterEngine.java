package com.krizaka.sovkora.core.application.routing;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deterministic router engine — selects the optimal provider using a composite scoring function.
 *
 * <p>Scoring rule:
 *
 * <pre>
 * score(provider) = cosineSimilarity(intentEmbedding, providerVector) × resilience4jHealthScore(providerId)
 * </pre>
 *
 * <p>Tie-break strategy: select the provider with the lowest p95 latency.
 *
 * <p>Uses lock-free {@link AtomicReference} over {@link ConcurrentHashMap} for thread-safe health
 * metric tracking, compliant with ERR-120 (no {@code synchronized} over I/O).
 */
public class DeterministicRouterEngine {

  private static final Logger log = LoggerFactory.getLogger(DeterministicRouterEngine.class);

  private final AtomicReference<Map<String, ProviderMetrics>> metricsRef =
      new AtomicReference<>(new ConcurrentHashMap<>());

  /**
   * Register or update metrics for a provider.
   *
   * @param providerId the unique provider identifier
   * @param metrics the current health and performance metrics
   */
  public void updateMetrics(String providerId, ProviderMetrics metrics) {
    metricsRef.get().put(providerId, metrics);
  }

  /**
   * Route an intent embedding to the optimal provider.
   *
   * @param intentEmbedding the intent's embedding vector
   * @return the selected provider ID, or empty if no providers are registered
   */
  public Optional<String> route(float[] intentEmbedding) {
    Map<String, ProviderMetrics> snapshot = metricsRef.get();
    if (snapshot.isEmpty()) {
      log.warn("No providers registered for routing");
      return Optional.empty();
    }

    String bestProvider = null;
    double bestScore = Double.NEGATIVE_INFINITY;
    double bestLatency = Double.MAX_VALUE;

    for (var entry : snapshot.entrySet()) {
      String providerId = entry.getKey();
      ProviderMetrics metrics = entry.getValue();

      double similarity =
          CosineSimilarityUtil.cosineSimilarity(intentEmbedding, metrics.providerVector());
      double score = similarity * metrics.healthScore();

      if (score > bestScore || (score == bestScore && metrics.p95LatencyMs() < bestLatency)) {
        bestScore = score;
        bestLatency = metrics.p95LatencyMs();
        bestProvider = providerId;
      }
    }

    log.debug(
        "Routed to provider={} with score={}, p95={}ms", bestProvider, bestScore, bestLatency);
    return Optional.ofNullable(bestProvider);
  }
}
