package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureRouteManager;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.FeatureModelEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.FeatureModelRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private implementation of the {@link FeatureRouteManager} inbound port. Not cached: the
 * routing table is edited by an administrator and the next turn must see it.
 */
@Service
@Transactional(readOnly = true)
class FeatureRouteManagerImpl implements FeatureRouteManager {

  private final FeatureModelRepository repository;

  FeatureRouteManagerImpl(FeatureModelRepository repository) {
    this.repository = Objects.requireNonNull(repository, "FeatureModelRepository cannot be null");
  }

  @Override
  public List<String> modelNames(String featureKey) {
    if (featureKey == null || featureKey.isBlank()) {
      return List.of();
    }
    return repository.findByFeatureKeyOrderByRankAsc(featureKey).stream()
        .map(FeatureModelEntity::getModelName)
        .toList();
  }

  @Override
  public Map<String, List<String>> allRoutes() {
    Map<String, List<String>> routes = new LinkedHashMap<>();
    repository.findAll().stream()
        .sorted(
            Comparator.comparing(FeatureModelEntity::getFeatureKey)
                .thenComparingInt(FeatureModelEntity::getRank))
        .forEach(
            row ->
                routes
                    .computeIfAbsent(row.getFeatureKey(), key -> new ArrayList<>())
                    .add(row.getModelName()));
    return routes;
  }
}
