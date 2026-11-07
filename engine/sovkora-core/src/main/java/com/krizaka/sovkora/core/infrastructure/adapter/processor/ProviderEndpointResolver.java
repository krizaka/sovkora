package com.krizaka.sovkora.core.infrastructure.adapter.processor;

import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;

/**
 * Resolves provider base URLs from the catalog, with a local fallback.
 *
 * <p>Replaces the former static {@code LocalProcessorUtil.resolveBaseUrl(CatalogModelManager)}: the
 * catalog dependency is held as a field, never passed as a method parameter [ERR-127]. Registered
 * as a bean in {@code CoreInfrastructureAutoConfiguration} and shared by the local audio/video
 * processors.
 */
public final class ProviderEndpointResolver {

  private static final String LOCALAI_FALLBACK = "http://" + "local" + "host:8085";

  private final CatalogModelManager catalogModelManager;

  public ProviderEndpointResolver(CatalogModelManager catalogModelManager) {
    this.catalogModelManager = catalogModelManager;
  }

  /**
   * Resolves the LocalAI base URL, falling back to {@code http://localhost:8085} when the catalog
   * has no {@code "localai"} provider entry.
   */
  public String localAiBaseUrl() {
    if (catalogModelManager != null) {
      String url = catalogModelManager.getProviderBaseUrl("localai");
      if (url != null && !url.isBlank()) {
        return url;
      }
    }
    return LOCALAI_FALLBACK;
  }
}
