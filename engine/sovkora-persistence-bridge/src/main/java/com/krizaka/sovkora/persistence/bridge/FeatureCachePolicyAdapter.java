package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.model.chat.CacheStrategy;
import com.krizaka.sovkora.core.domain.ports.outbound.PromptCachePolicy;
import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureCachePolicyManager;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * DB-backed {@link PromptCachePolicy} adapter (ADR-094 slice 5, #173): reads {@code
 * orazaka_feature_cache_policy} through the {@link FeatureCachePolicyManager} inbound port, so
 * {@code sovkora-core} stays free of any persistence import. With no row at all, or a value this
 * version does not know, the safe default of the port applies.
 */
@Service
class FeatureCachePolicyAdapter implements PromptCachePolicy {

  private final FeatureCachePolicyManager policy;

  FeatureCachePolicyAdapter(FeatureCachePolicyManager policy) {
    this.policy = Objects.requireNonNull(policy, "FeatureCachePolicyManager cannot be null");
  }

  @Override
  public CacheStrategy strategy(String featureKey) {
    return policy
        .strategy(featureKey)
        .map(FeatureCachePolicyAdapter::parse)
        .orElse(DEFAULT_STRATEGY);
  }

  private static CacheStrategy parse(String stored) {
    try {
      return CacheStrategy.valueOf(stored);
    } catch (IllegalArgumentException e) {
      return DEFAULT_STRATEGY;
    }
  }
}
