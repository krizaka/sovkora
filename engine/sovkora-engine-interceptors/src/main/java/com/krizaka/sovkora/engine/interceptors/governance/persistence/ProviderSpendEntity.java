package com.krizaka.sovkora.engine.interceptors.governance.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** JPA entity of {@code provider_spend}: a scope's spend in one calendar month. */
@Entity
@Table(name = "provider_spend")
class ProviderSpendEntity {

  @Id
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "environment", nullable = false)
  private String environment;

  @Column(name = "product_key", nullable = false)
  private String productKey;

  @Column(name = "month", nullable = false)
  private LocalDate month;

  @Column(name = "spent_usd", nullable = false)
  private BigDecimal spentUsd;

  /** For JPA. */
  protected ProviderSpendEntity() {
    // loaded by the persistence provider
  }

  BigDecimal getSpentUsd() {
    return spentUsd;
  }
}
