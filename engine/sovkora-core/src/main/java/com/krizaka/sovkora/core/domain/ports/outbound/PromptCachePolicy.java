package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.chat.CacheStrategy;

/**
 * Outbound port (ADR-094 slice 5, #173): what of a turn's prompt the provider may cache, per
 * <b>feature key</b>. The adapter reads a row an administrator edits ({@code
 * orazaka_feature_cache_policy}); the policy is data, never a constant a deploy has to change. A
 * SENSITIVE turn is never asked here: it is never cached, whatever the policy says.
 */
@FunctionalInterface
public interface PromptCachePolicy {

  /** The strategy when nothing is configured (the seeded default row says the same). */
  CacheStrategy DEFAULT_STRATEGY = CacheStrategy.SYSTEM_AND_TOOLS; // @fixed: safe default, no row

  /**
   * @param featureKey the turn's feature, or {@code null} for a turn with none (the default row)
   * @return what may be cached, never {@code null}
   */
  CacheStrategy strategy(String featureKey);

  /** A policy that is the same for every feature. */
  static PromptCachePolicy fixed(CacheStrategy strategy) {
    java.util.Objects.requireNonNull(strategy, "strategy");
    return featureKey -> strategy;
  }
}
