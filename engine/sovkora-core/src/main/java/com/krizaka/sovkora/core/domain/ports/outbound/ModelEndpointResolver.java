package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import java.util.Optional;

/**
 * Outbound port (ADR-030): resolves a model to its DB-backed {@link ModelEndpoint} — the provider,
 * base-url and api-key needed to build a client. The implementing adapter reads {@code
 * orazaka_models} (model → {@code provider_name}, {@code is_default}) and {@code ai_providers}
 * (provider → base-url/api-key), keeping {@code sovkora-core} free of any persistence import.
 */
public interface ModelEndpointResolver {

  /** Resolves the endpoint for an explicit model name. */
  Optional<ModelEndpoint> resolve(String modelName);

  /** Resolves the endpoint for a capability category's default model ({@code is_default}). */
  Optional<ModelEndpoint> resolveDefault(String category);

  /**
   * Resolves the endpoint a feature key routes to (ADR-029): the best-ranked model of its routing
   * rows whose provider is reachable, else the default of the fallback category.
   *
   * @param featureKey the feature, e.g. {@code agent.code.issue-to-pr}
   * @param fallbackCategory the category whose default answers when the feature has no usable row
   * @return the endpoint
   */
  default Optional<ModelEndpoint> resolveForFeature(String featureKey, String fallbackCategory) {
    return resolveDefault(fallbackCategory);
  }
}
