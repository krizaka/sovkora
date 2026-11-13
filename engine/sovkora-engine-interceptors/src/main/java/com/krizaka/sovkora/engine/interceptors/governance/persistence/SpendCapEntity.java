package com.krizaka.sovkora.engine.interceptors.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** JPA entity of {@code provider_spend_cap}: one cap row per environment and product key. */
@Entity
@Table(name = "provider_spend_cap")
class SpendCapEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id = UUID.randomUUID();

  @Column(name = "environment", nullable = false, updatable = false)
  private String environment;

  @Column(name = "product_key", nullable = false, updatable = false)
  private String productKey;

  @Column(name = "monthly_cap_usd", nullable = false)
  private BigDecimal monthlyCapUsd;

  @Column(name = "action", nullable = false)
  private String action;

  @Column(name = "downgrade_model_key")
  private String downgradeModelKey;

  /** For JPA. */
  protected SpendCapEntity() {
    // loaded by the persistence provider
  }

  SpendCapEntity(String environment, String productKey) {
    this.environment = environment;
    this.productKey = productKey;
  }

  String getEnvironment() {
    return environment;
  }

  String getProductKey() {
    return productKey;
  }

  BigDecimal getMonthlyCapUsd() {
    return monthlyCapUsd;
  }

  String getAction() {
    return action;
  }

  String getDowngradeModelKey() {
    return downgradeModelKey;
  }

  void change(BigDecimal monthlyCapUsd, String action, String downgradeModelKey) {
    this.monthlyCapUsd = monthlyCapUsd;
    this.action = action;
    this.downgradeModelKey = downgradeModelKey;
  }
}
