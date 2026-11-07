package com.krizaka.sovkora.persistence.domain.ports.inbound;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What of a feature's prompt a provider may cache (ADR-094 slice 5, sovkora-engine#173): {@code
 * NONE}, {@code SYSTEM_ONLY} or {@code SYSTEM_AND_TOOLS}. The policy is data an administrator
 * edits, kept in {@code orazaka_feature_cache_policy} ({@code feature_key}, {@code strategy}),
 * never a constant in code. The row {@code *} is the default of every feature without a row of its
 * own. A SENSITIVE turn is never cached, whatever the row says.
 */
public interface FeatureCachePolicyManager {

  /** The feature key of the default row. */
  String DEFAULT_FEATURE_KEY = "*";

  /** The strategies a row may hold (the database CHECK says the same). */
  Set<String> STRATEGIES = Set.of("NONE", "SYSTEM_ONLY", "SYSTEM_AND_TOOLS");

  /**
   * Reads one feature's strategy.
   *
   * @param featureKey a feature key; blank means the default
   * @return the feature's own row or else the default row; empty if neither exists (the caller then
   *     applies its own safe default)
   */
  Optional<String> strategy(String featureKey);

  /** Every row by feature key, the default row {@code *} included, sorted by key. */
  Map<String, String> all();

  /**
   * Writes a row (an administrator action; the caller audits it).
   *
   * @param featureKey the feature or {@code *}; non-blank and at most 120 characters
   * @param strategy one of {@link #STRATEGIES}
   * @throws IllegalArgumentException on a bad key or an unknown strategy
   */
  void upsert(String featureKey, String strategy);

  /**
   * Removes a feature's own policy, so the feature follows the default row again.
   *
   * @param featureKey the feature whose row goes; the default row's key is refused
   * @return true when a row existed and was removed
   * @throws IllegalArgumentException when asked to remove the default row
   */
  boolean remove(String featureKey);
}
