package com.krizaka.sovkora.engine.interceptors.governance;

import java.math.BigDecimal;

/**
 * The monthly provider-spend cap of one scope, as the row stores it.
 *
 * @param scope what the cap is counted against
 * @param monthlyCapUsd the most the scope may spend with providers in a calendar month, in USD
 * @param action what happens when the month's spend reaches the cap
 * @param downgradeModelKey the cheaper chat model served instead under {@link
 *     SpendCapAction#DOWNGRADE}; {@code null} under {@link SpendCapAction#REFUSE}
 */
public record SpendCap(
    SpendScope scope, BigDecimal monthlyCapUsd, SpendCapAction action, String downgradeModelKey) {

  public SpendCap {
    if (scope == null || action == null) {
      throw new IllegalArgumentException("SpendCap requires a scope and an action");
    }
    if (monthlyCapUsd == null || monthlyCapUsd.signum() <= 0) {
      throw new IllegalArgumentException("monthlyCapUsd must be greater than zero");
    }
    if (action == SpendCapAction.DOWNGRADE
        && (downgradeModelKey == null || downgradeModelKey.isBlank())) {
      throw new IllegalArgumentException("a DOWNGRADE cap names the cheaper model to serve");
    }
    if (action == SpendCapAction.REFUSE) {
      downgradeModelKey = null;
    }
  }

  /**
   * How much of the cap a month's spend is.
   *
   * @param spentUsd the month's spend
   * @return spend divided by cap ({@code 1.0} is exactly at the cap)
   */
  public double ratio(BigDecimal spentUsd) {
    return spentUsd.divide(monthlyCapUsd, 6, java.math.RoundingMode.HALF_UP).doubleValue();
  }

  /**
   * Whether the month's spend has reached the cap.
   *
   * @param spentUsd the month's spend
   * @return {@code true} at or above the cap
   */
  public boolean reachedBy(BigDecimal spentUsd) {
    return spentUsd.compareTo(monthlyCapUsd) >= 0;
  }
}
