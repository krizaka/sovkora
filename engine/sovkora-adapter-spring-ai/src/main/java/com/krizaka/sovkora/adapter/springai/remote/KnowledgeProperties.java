package com.krizaka.sovkora.adapter.springai.remote;

import java.net.URI;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Wiring of the knowledge service consumed by the core's RAG port ({@code orazaka.core.knowledge}).
 *
 * @param baseUrl the knowledge service base URL
 */
@ConfigurationProperties(prefix = "orazaka.core.knowledge")
public record KnowledgeProperties(URI baseUrl, String serviceSecret) {

  public KnowledgeProperties {
    Objects.requireNonNull(baseUrl, "knowledge base-url is required");
    if (baseUrl.getHost() == null) {
      throw new IllegalArgumentException("knowledge base-url must be absolute: " + baseUrl);
    }
  }
}
