package com.krizaka.sovkora.adapter.springai.routing;

import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import com.krizaka.sovkora.core.domain.ports.outbound.FeatureModelRoutes;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelEndpointResolver;
import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;

/**
 * DB-backed {@link ModelEndpointResolver} adapter (ADR-030).
 *
 * <p>Reads {@code orazaka_models} (model → {@code provider_name}, {@code is_default} per category)
 * and {@code ai_providers} (provider → base-url) through the {@link CatalogModelManager} inbound
 * port, so {@code sovkora-core}'s {@code DynamicModelRouter} (in the Spring AI adapter) stays free
 * of any persistence import. The endpoint carries the provider's own key ({@code
 * ai_providers.api_key}): none for a local provider (Ollama/LocalAI), the deployment's key for a
 * hosted OpenAI-compatible one (serverless inference). Commercial per-user keys still flow through
 * the engine's {@code ModelFactory} path.
 */
public class CatalogModelEndpointResolver implements ModelEndpointResolver {

  private final CatalogModelManager catalog;
  private final FeatureModelRoutes routes;

  /**
   * The Spring constructor: the routing rows are optional, a deployment without an adapter for them
   * routes every feature to its category default.
   */
  public CatalogModelEndpointResolver(
      CatalogModelManager catalog, ObjectProvider<FeatureModelRoutes> routes) {
    this(catalog, routes.getIfAvailable(() -> feature -> List.of()));
  }

  public CatalogModelEndpointResolver(CatalogModelManager catalog, FeatureModelRoutes routes) {
    this.catalog = Objects.requireNonNull(catalog, "CatalogModelManager cannot be null");
    this.routes = Objects.requireNonNull(routes, "FeatureModelRoutes cannot be null");
  }

  @Override
  public Optional<ModelEndpoint> resolve(String modelName) {
    if (modelName == null || modelName.isBlank()) {
      return Optional.empty();
    }
    return catalog.getAllModels().stream()
        .filter(m -> modelName.equalsIgnoreCase(m.modelName()))
        .findFirst()
        .flatMap(this::toEndpoint);
  }

  @Override
  public Optional<ModelEndpoint> resolveDefault(String category) {
    return catalog.getDefaultModelByCategory(category).flatMap(this::toEndpoint);
  }

  @Override
  public Optional<ModelEndpoint> resolveForFeature(String featureKey, String fallbackCategory) {
    if (featureKey != null && !featureKey.isBlank()) {
      for (String modelName : routes.modelNames(featureKey)) {
        Optional<ModelEndpoint> routed = resolve(modelName);
        if (routed.isPresent()) {
          return routed;
        }
      }
    }
    return resolveDefault(fallbackCategory);
  }

  private Optional<ModelEndpoint> toEndpoint(CatalogModelDto model) {
    String baseUrl = catalog.getProviderBaseUrl(model.providerName());
    if (baseUrl == null || baseUrl.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(
        new ModelEndpoint(
            model.providerName(),
            baseUrl,
            catalog.getProviderApiKey(model.providerName()),
            model.modelName()));
  }
}
