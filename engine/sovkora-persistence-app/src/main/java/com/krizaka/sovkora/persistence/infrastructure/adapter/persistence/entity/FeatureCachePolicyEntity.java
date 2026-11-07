package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity of {@code orazaka_feature_cache_policy}: what of a feature key's prompt a provider may
 * cache. The row {@code *} is the default of every feature without a row.
 */
@Entity
@Table(name = "orazaka_feature_cache_policy")
public class FeatureCachePolicyEntity {

  @Id
  @Column(name = "feature_key", nullable = false, length = 120)
  private String featureKey;

  @Column(name = "strategy", nullable = false, length = 32)
  private String strategy;

  public String getFeatureKey() {
    return featureKey;
  }

  public void setFeatureKey(String featureKey) {
    this.featureKey = featureKey;
  }

  public String getStrategy() {
    return strategy;
  }

  public void setStrategy(String strategy) {
    this.strategy = strategy;
  }
}
