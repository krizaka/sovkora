package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.FeatureStructuredBoundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** JPA repository of {@code orazaka_feature_structured_bounds}, by feature key. */
@Repository
public interface FeatureStructuredBoundRepository
    extends JpaRepository<FeatureStructuredBoundEntity, String> {}
