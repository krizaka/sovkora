package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.FeatureModelEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** JPA repository of {@code orazaka_feature_models}. */
@Repository
public interface FeatureModelRepository
    extends JpaRepository<FeatureModelEntity, FeatureModelEntity.Key> {

  /** The rows of one feature key, best rank first. */
  List<FeatureModelEntity> findByFeatureKeyOrderByRankAsc(String featureKey);
}
