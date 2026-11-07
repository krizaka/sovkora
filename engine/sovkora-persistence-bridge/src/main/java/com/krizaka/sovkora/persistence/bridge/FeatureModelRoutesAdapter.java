package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.ports.outbound.FeatureModelRoutes;
import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureRouteManager;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * DB-backed {@link FeatureModelRoutes} adapter (ADR-029): reads {@code orazaka_feature_models}
 * through the {@link FeatureRouteManager} inbound port, so {@code sovkora-core} stays free of any
 * persistence import. A deployment without the port wired keeps {@link
 * CatalogModelEndpointResolver}'s default (every feature routes to its category default).
 */
@Service
class FeatureModelRoutesAdapter implements FeatureModelRoutes {

  private final FeatureRouteManager routes;

  FeatureModelRoutesAdapter(FeatureRouteManager routes) {
    this.routes = Objects.requireNonNull(routes, "FeatureRouteManager cannot be null");
  }

  @Override
  public List<String> modelNames(String featureKey) {
    return routes.modelNames(featureKey);
  }
}
