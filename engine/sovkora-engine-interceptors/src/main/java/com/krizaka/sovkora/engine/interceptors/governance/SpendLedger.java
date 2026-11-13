package com.krizaka.sovkora.engine.interceptors.governance;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/**
 * The cap, price and spend rows the provider-spend control runs on. The interceptor reads it, the
 * settlement listener is its one writer.
 */
public interface SpendLedger {

  /** The scope's cap row, if it has one. */
  Optional<SpendCap> capOf(SpendScope scope);

  /** What the scope has spent with providers in the calendar month; zero when nothing yet. */
  BigDecimal spentIn(SpendScope scope, YearMonth month);

  /** What the provider charges for a model, if a price row exists. */
  Optional<ProviderPrice> priceOf(String modelKey);

  /**
   * Adds a turn's cost to the scope's month, atomically.
   *
   * @return the month's spend once the cost is in
   */
  BigDecimal add(SpendScope scope, YearMonth month, BigDecimal usd);

  /** The percentages of a cap at which an alert fires, as rows (50, 80, 100). */
  List<Integer> alertThresholds();

  /**
   * Records that an alert fired for a scope, a month and a threshold.
   *
   * @return {@code true} the first time, {@code false} when it had already fired
   */
  boolean markAlertFired(SpendScope scope, YearMonth month, int percent);
}
