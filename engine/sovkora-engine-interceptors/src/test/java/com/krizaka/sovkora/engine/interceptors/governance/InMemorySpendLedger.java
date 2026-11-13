package com.krizaka.sovkora.engine.interceptors.governance;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A ledger in memory for the unit tests of the control, with a switch that makes it unreadable. */
class InMemorySpendLedger implements SpendLedger {

  final Map<SpendScope, SpendCap> caps = new HashMap<>();
  final Map<String, ProviderPrice> prices = new HashMap<>();
  final Map<String, BigDecimal> spend = new HashMap<>();
  final Set<String> fired = new HashSet<>();
  boolean unreadable;

  private static String key(SpendScope scope, YearMonth month) {
    return scope.label() + "@" + month;
  }

  @Override
  public Optional<SpendCap> capOf(SpendScope scope) {
    failIfUnreadable();
    return Optional.ofNullable(caps.get(scope));
  }

  @Override
  public BigDecimal spentIn(SpendScope scope, YearMonth month) {
    failIfUnreadable();
    return spend.getOrDefault(key(scope, month), BigDecimal.ZERO);
  }

  @Override
  public Optional<ProviderPrice> priceOf(String modelKey) {
    return Optional.ofNullable(prices.get(modelKey));
  }

  @Override
  public BigDecimal add(SpendScope scope, YearMonth month, BigDecimal usd) {
    return spend.merge(key(scope, month), usd, BigDecimal::add);
  }

  @Override
  public List<Integer> alertThresholds() {
    return List.of(50, 80, 100);
  }

  @Override
  public boolean markAlertFired(SpendScope scope, YearMonth month, int percent) {
    return fired.add(key(scope, month) + "#" + percent);
  }

  private void failIfUnreadable() {
    if (unreadable) {
      throw new IllegalStateException("the counter is down");
    }
  }
}
