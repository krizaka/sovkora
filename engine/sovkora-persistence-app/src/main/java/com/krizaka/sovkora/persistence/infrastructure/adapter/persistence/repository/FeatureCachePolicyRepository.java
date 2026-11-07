package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.FeatureCachePolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Rows of {@code orazaka_feature_cache_policy}, by feature key. */
public interface FeatureCachePolicyRepository
    extends JpaRepository<FeatureCachePolicyEntity, String> {}
