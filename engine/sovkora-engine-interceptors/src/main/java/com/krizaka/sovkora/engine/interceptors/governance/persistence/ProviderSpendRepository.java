package com.krizaka.sovkora.engine.interceptors.governance.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Spring Data repository of the monthly provider spend. */
public interface ProviderSpendRepository extends JpaRepository<ProviderSpendEntity, UUID> {

  /** The spend of one scope and month, if any turn has been counted in it. */
  Optional<ProviderSpendEntity> findByEnvironmentAndProductKeyAndMonth(
      String environment, String productKey, LocalDate month);

  /**
   * Adds to a scope's month in one statement, so two turns finishing together both count: the row
   * is created by the first and incremented by the others, never read-then-written.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "INSERT INTO provider_spend (id, environment, product_key, month, spent_usd)"
              + " VALUES (:id, :environment, :productKey, :month, :usd)"
              + " ON CONFLICT (environment, product_key, month)"
              + " DO UPDATE SET spent_usd = provider_spend.spent_usd + EXCLUDED.spent_usd",
      nativeQuery = true)
  int add(
      @Param("id") UUID id,
      @Param("environment") String environment,
      @Param("productKey") String productKey,
      @Param("month") LocalDate month,
      @Param("usd") BigDecimal usd);

  /** Registers an alert as fired; zero rows when it already had. */
  @Modifying
  @Query(
      value =
          "INSERT INTO provider_spend_alert (id, environment, product_key, month, percent)"
              + " VALUES (:id, :environment, :productKey, :month, :percent)"
              + " ON CONFLICT (environment, product_key, month, percent) DO NOTHING",
      nativeQuery = true)
  int markAlertFired(
      @Param("id") UUID id,
      @Param("environment") String environment,
      @Param("productKey") String productKey,
      @Param("month") LocalDate month,
      @Param("percent") int percent);

  /** The alert percentages, ascending. */
  @Query(
      value = "SELECT percent FROM provider_spend_threshold ORDER BY percent",
      nativeQuery = true)
  java.util.List<Integer> thresholds();

  /** Records one change of a cap: what it was and what it became. */
  @Modifying
  @Query(
      value =
          "INSERT INTO provider_spend_cap_change (id, environment, product_key, old_cap_usd,"
              + " new_cap_usd, old_action, new_action) VALUES (:id, :environment, :productKey,"
              + " :oldCap, :newCap, :oldAction, :newAction)",
      nativeQuery = true)
  int recordChange(
      @Param("id") UUID id,
      @Param("environment") String environment,
      @Param("productKey") String productKey,
      @Param("oldCap") BigDecimal oldCap,
      @Param("newCap") BigDecimal newCap,
      @Param("oldAction") String oldAction,
      @Param("newAction") String newAction);
}
