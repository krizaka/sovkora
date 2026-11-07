package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/** JPA entity of {@code orazaka_feature_models}: a feature key's models, best rank first. */
@Entity
@Table(name = "orazaka_feature_models")
@IdClass(FeatureModelEntity.Key.class)
public class FeatureModelEntity {

  @Id
  @Column(name = "feature_key", nullable = false, length = 120)
  private String featureKey;

  @Id
  @Column(name = "rank", nullable = false)
  private int rank;

  @Column(name = "model_name", nullable = false)
  private String modelName;

  public String getFeatureKey() {
    return featureKey;
  }

  public void setFeatureKey(String featureKey) {
    this.featureKey = featureKey;
  }

  public int getRank() {
    return rank;
  }

  public void setRank(int rank) {
    this.rank = rank;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  /** The composite primary key {@code (feature_key, rank)}. */
  public static class Key implements Serializable {
    private String featureKey;
    private int rank;

    /** For JPA. */
    public Key() {
      // set by the persistence provider
    }

    public Key(String featureKey, int rank) {
      this.featureKey = featureKey;
      this.rank = rank;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && rank == key.rank
          && Objects.equals(featureKey, key.featureKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(featureKey, rank);
    }
  }
}
