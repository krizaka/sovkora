package com.krizaka.sovkora.knowledge.recommendation.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Wiring of the recommender (infrastructure only — its parameters are data, ADR-078 §5).
 *
 * @param embeddingsBaseUrl the conversation service, which serves {@code /api/v1/embeddings}
 * @param embeddingsTimeout how long an embedding call may take
 */
@ConfigurationProperties("orazaka.knowledge-service.recommendation")
public record RecommendationProperties(URI embeddingsBaseUrl, Duration embeddingsTimeout) {

  public RecommendationProperties {
    Objects.requireNonNull(embeddingsBaseUrl, "embeddings-base-url is required");
    embeddingsTimeout = embeddingsTimeout == null ? Duration.ofSeconds(20) : embeddingsTimeout;
  }
}
