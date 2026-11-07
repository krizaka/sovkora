package com.krizaka.sovkora.engine.persistence.api.domain.ports.inbound;

import java.util.List;
import java.util.Map;

/**
 * The models a <b>feature key</b> routes to (ADR-029, ADR-093): routing is data an administrator
 * edits, kept in {@code orazaka_feature_models} ({@code feature_key}, {@code rank}, {@code
 * model_name}), never an {@code @Order}, a name heuristic or a constant in code.
 *
 * <p>It sits beside {@link CatalogModelManager} because a feature's route is a different fact from
 * a model's category default: a feature with no row simply has none, and the caller falls back to
 * the category default.
 */
public interface FeatureRouteManager {

  /**
   * @param featureKey the feature, e.g. {@code agent.code.issue-to-pr}
   * @return the registry model names in rank order (best first); empty when the feature has no row
   *     or the key is blank, which is not an error
   */
  List<String> modelNames(String featureKey);

  /**
   * @return every feature that has a route, by key (sorted), with its model names in rank order;
   *     read-only, for the administrator's console
   */
  Map<String, List<String>> allRoutes();
}
