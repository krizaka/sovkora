package com.krizaka.sovkora.engine.interceptors.governance.persistence;

import com.krizaka.sovkora.engine.interceptors.governance.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database side of the provider-spend control: the cap, price and spend rows, read and written
 * as domain records, with the JPA entities and repositories kept inside this sub-package.
 */
public class SpendLedgerStore implements SpendLedger, SpendCapAdministration {

  private final SpendCapRepository caps;
  private final ProviderPriceRepository prices;
  private final ProviderSpendRepository spend;

  public SpendLedgerStore(
      SpendCapRepository caps, ProviderPriceRepository prices, ProviderSpendRepository spend) {
    this.caps = caps;
    this.prices = prices;
    this.spend = spend;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SpendCap> capOf(SpendScope scope) {
    return caps.findByEnvironmentAndProductKey(scope.environment(), scope.productKey())
        .map(SpendLedgerStore::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal spentIn(SpendScope scope, YearMonth month) {
    return spend
        .findByEnvironmentAndProductKeyAndMonth(
            scope.environment(), scope.productKey(), month.atDay(1))
        .map(ProviderSpendEntity::getSpentUsd)
        .orElse(BigDecimal.ZERO);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ProviderPrice> priceOf(String modelKey) {
    return prices.findById(modelKey).map(SpendLedgerStore::toDomain);
  }

  @Override
  @Transactional
  public BigDecimal add(SpendScope scope, YearMonth month, BigDecimal usd) {
    spend.add(UUID.randomUUID(), scope.environment(), scope.productKey(), month.atDay(1), usd);
    return spentIn(scope, month);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Integer> alertThresholds() {
    return spend.thresholds();
  }

  @Override
  @Transactional
  public boolean markAlertFired(SpendScope scope, YearMonth month, int percent) {
    return spend.markAlertFired(
            UUID.randomUUID(), scope.environment(), scope.productKey(), month.atDay(1), percent)
        == 1;
  }

  @Override
  @Transactional(readOnly = true)
  public List<SpendCap> caps() {
    return caps.findAllByOrderByEnvironmentAscProductKeyAsc().stream()
        .map(SpendLedgerStore::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProviderPrice> prices() {
    return prices.findAllByOrderByModelKeyAsc().stream().map(SpendLedgerStore::toDomain).toList();
  }

  @Override
  @Transactional
  public SpendCap put(SpendCap cap) {
    if (cap.action() == SpendCapAction.DOWNGRADE) {
      ProviderPriceEntity target =
          prices
              .findById(cap.downgradeModelKey())
              .orElseThrow(
                  () -> new IllegalArgumentException("the downgrade model has no price row"));
      if (!"CHAT".equals(target.getCapability())) {
        throw new IllegalArgumentException("the downgrade model must serve the CHAT capability");
      }
    }
    SpendCapEntity row =
        caps.findByEnvironmentAndProductKey(cap.scope().environment(), cap.scope().productKey())
            .orElseGet(
                () -> new SpendCapEntity(cap.scope().environment(), cap.scope().productKey()));
    BigDecimal oldCap = row.getMonthlyCapUsd();
    String oldAction = row.getAction();
    row.change(cap.monthlyCapUsd(), cap.action().name(), cap.downgradeModelKey());
    caps.save(row);
    spend.recordChange(
        UUID.randomUUID(),
        cap.scope().environment(),
        cap.scope().productKey(),
        oldCap,
        cap.monthlyCapUsd(),
        oldAction,
        cap.action().name());
    return cap;
  }

  private static SpendCap toDomain(SpendCapEntity row) {
    return new SpendCap(
        new SpendScope(row.getEnvironment(), row.getProductKey()),
        row.getMonthlyCapUsd(),
        SpendCapAction.valueOf(row.getAction()),
        row.getDowngradeModelKey());
  }

  private static ProviderPrice toDomain(ProviderPriceEntity row) {
    return new ProviderPrice(
        row.getModelKey(),
        row.getCapability(),
        row.getInputUsdPerMillionTokens(),
        row.getOutputUsdPerMillionTokens());
  }
}
