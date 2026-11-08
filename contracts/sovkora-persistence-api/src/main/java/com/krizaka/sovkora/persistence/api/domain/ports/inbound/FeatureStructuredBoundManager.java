package com.krizaka.sovkora.persistence.api.domain.ports.inbound;

import java.util.Map;
import java.util.OptionalInt;

/**
 * The most answers a structured-output call of a <b>feature key</b> may ask a model for (ADR-094
 * §4.6, sovkora-engine#172): the first plus the corrections that name the schema violation. The
 * bound is data an administrator edits, kept in {@code orazaka_feature_structured_bounds} ({@code
 * feature_key}, {@code max_attempts}, 1 to 5), never a constant in code. The row {@code *} is the
 * default of every feature without a row of its own.
 */
public interface FeatureStructuredBoundManager {

  /** The feature key of the default row. */
  String DEFAULT_FEATURE_KEY = "*";

  /** The fewest attempts a bound may allow (the database CHECK says the same). */
  int MIN_ATTEMPTS = 1;

  /** The most attempts a bound may allow (the database CHECK says the same). */
  int MAX_ATTEMPTS = 5;

  /**
   * Reads one feature's attempts.
   *
   * @param featureKey a feature key such as {@code agent.code.issue-to-pr}; blank means the default
   * @return the feature's own row or else the default row; empty if neither exists (the caller then
   *     applies its own safe default)
   */
  OptionalInt maxAttempts(String featureKey);

  /** Every row by feature key, the default row {@code *} included, sorted by key. */
  Map<String, Integer> all();

  /**
   * Writes a row (an administrator action; the caller audits it).
   *
   * @param featureKey the feature or {@code *}; non-blank and at most 120 characters
   * @param maxAttempts between {@link #MIN_ATTEMPTS} and {@link #MAX_ATTEMPTS}
   * @throws IllegalArgumentException on a bad key or an attempts value out of range
   */
  void upsert(String featureKey, int maxAttempts);

  /**
   * Drops a feature's own row so it reads the default again; the default row stays.
   *
   * @return whether a row was dropped
   * @throws IllegalArgumentException for the default row's key
   */
  boolean delete(String featureKey);
}
