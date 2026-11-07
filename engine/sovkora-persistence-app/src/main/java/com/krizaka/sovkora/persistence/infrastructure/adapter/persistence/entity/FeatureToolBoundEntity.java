package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity of {@code orazaka_feature_tool_bounds}: the most tool rounds a feature key's turn may
 * take. The row {@code *} is the default of every feature without a row.
 */
@Entity
@Table(name = "orazaka_feature_tool_bounds")
public class FeatureToolBoundEntity {

  @Id
  @Column(name = "feature_key", nullable = false, length = 120)
  private String featureKey;

  @Column(name = "max_tool_rounds", nullable = false)
  private int maxToolRounds;

  public String getFeatureKey() {
    return featureKey;
  }

  public void setFeatureKey(String featureKey) {
    this.featureKey = featureKey;
  }

  public int getMaxToolRounds() {
    return maxToolRounds;
  }

  public void setMaxToolRounds(int maxToolRounds) {
    this.maxToolRounds = maxToolRounds;
  }
}
