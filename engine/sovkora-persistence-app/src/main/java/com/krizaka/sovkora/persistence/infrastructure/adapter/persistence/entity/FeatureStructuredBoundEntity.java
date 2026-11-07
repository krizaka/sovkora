package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity of {@code orazaka_feature_structured_bounds}: the most answers a feature key's
 * structured-output call may ask for. The row {@code *} is the default of every feature without a
 * row.
 */
@Entity
@Table(name = "orazaka_feature_structured_bounds")
public class FeatureStructuredBoundEntity {

  @Id
  @Column(name = "feature_key", nullable = false, length = 120)
  private String featureKey;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  public String getFeatureKey() {
    return featureKey;
  }

  public void setFeatureKey(String featureKey) {
    this.featureKey = featureKey;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }
}
