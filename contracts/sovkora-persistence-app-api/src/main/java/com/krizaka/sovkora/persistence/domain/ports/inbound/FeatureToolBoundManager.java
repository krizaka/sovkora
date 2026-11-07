package com.krizaka.sovkora.persistence.domain.ports.inbound;

import java.util.Map;
import java.util.OptionalInt;

/**
 * The most tool rounds a <b>feature key</b> may run in one turn (ADR-029, sovkora-engine#169): the
 * bound is data an administrator edits, kept in {@code orazaka_feature_tool_bounds} ({@code
 * feature_key}, {@code max_tool_rounds}, 1 to 32), never a constant in code. The row {@code *} is
 * the default of every feature without a row of its own.
 */
public interface FeatureToolBoundManager {

  /** The feature key of the default row. */
  String DEFAULT_FEATURE_KEY = "*";

  /** The fewest rounds a bound may allow (the database CHECK says the same). */
  int MIN_ROUNDS = 1;

  /** The most rounds a bound may allow (the database CHECK says the same). */
  int MAX_ROUNDS = 32;

  /**
   * @param featureKey the feature, e.g. {@code agent.code.issue-to-pr}; {@code null} or blank reads
   *     the default row
   * @return the bound of the feature's own row, else the default row's; empty only when neither
   *     exists, in which case the caller keeps its own safe default
   */
  OptionalInt maxToolRounds(String featureKey);

  /**
   * @return every bound by feature key (the default row {@code *} included), sorted by key
   */
  Map<String, Integer> all();

  /**
   * Creates or replaces the bound of a feature (administrator action, audited by the caller).
   *
   * @param featureKey the feature, or {@code *} for the default row; non-blank, at most 120
   *     characters
   * @param maxToolRounds from {@link #MIN_ROUNDS} to {@link #MAX_ROUNDS}
   * @throws IllegalArgumentException when the key is blank or too long or the rounds are out of
   *     range
   */
  void upsert(String featureKey, int maxToolRounds);

  /**
   * Removes a feature's own bound so it falls back to the default row. The default row itself is
   * never removed.
   *
   * @return {@code true} when a row was removed
   * @throws IllegalArgumentException when the key is the default row's
   */
  boolean delete(String featureKey);
}
