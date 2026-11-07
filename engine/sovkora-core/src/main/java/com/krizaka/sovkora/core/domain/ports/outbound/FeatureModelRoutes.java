package com.krizaka.sovkora.core.domain.ports.outbound;

import java.util.List;

/**
 * Outbound port (ADR-029): the models a <b>feature key</b> routes to, best first. The adapter reads
 * {@code orazaka_feature_models} ({@code feature_key}, {@code rank}, {@code model_name}); routing
 * is data an administrator edits, never an {@code @Order}, a name heuristic or a constant here.
 */
public interface FeatureModelRoutes {

  /**
   * @param featureKey the feature, e.g. {@code agent.code.issue-to-pr}
   * @return the registry model names in rank order; empty when the feature has no row, which is not
   *     an error: the caller falls back to the category default
   */
  List<String> modelNames(String featureKey);
}
