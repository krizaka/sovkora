package com.krizaka.sovkora.core.domain.model;

import java.util.Objects;

/**
 * A resolved model endpoint (ADR-030): everything needed to build a client for one model, sourced
 * from the DB ({@code orazaka_models} → {@code provider_name}; {@code ai_providers} →
 * base-url/api-key).
 *
 * @param providerName the provider name (e.g. {@code ollama}, {@code localai}, {@code openai})
 * @param baseUrl the provider base-url (e.g. {@code http://localhost:11434/v1})
 * @param apiKey the provider api-key ({@code "not-required"} for local providers)
 * @param modelName the model name from {@code orazaka_models}
 */
public record ModelEndpoint(String providerName, String baseUrl, String apiKey, String modelName) {

  public ModelEndpoint {
    Objects.requireNonNull(providerName, "providerName cannot be null");
    Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
    Objects.requireNonNull(modelName, "modelName cannot be null");
    if (apiKey == null || apiKey.isBlank()) {
      apiKey = "not-required";
    }
  }
}
