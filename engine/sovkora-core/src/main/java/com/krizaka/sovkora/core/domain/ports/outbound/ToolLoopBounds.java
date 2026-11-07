package com.krizaka.sovkora.core.domain.ports.outbound;

/**
 * Outbound port (ADR-029, #169): the most tool rounds one turn of a <b>feature key</b> may take.
 * The adapter reads a row an administrator edits ({@code orazaka_feature_tool_bounds}); the number
 * is data, never a constant a deploy has to change.
 */
@FunctionalInterface
public interface ToolLoopBounds {

  /** The bound when nothing is configured (the seeded default row says the same). */
  int DEFAULT_MAX_ROUNDS = 8; // @fixed: the safe default when no row is readable

  /**
   * @param featureKey the turn's feature, or {@code null} for a turn with none (the default row)
   * @return the bound, at least 1
   */
  int maxRounds(String featureKey);

  /** A bound that is the same for every feature. */
  static ToolLoopBounds fixed(int maxRounds) {
    if (maxRounds < 1) {
      throw new IllegalArgumentException("maxToolRounds must be at least 1");
    }
    return featureKey -> maxRounds;
  }
}
