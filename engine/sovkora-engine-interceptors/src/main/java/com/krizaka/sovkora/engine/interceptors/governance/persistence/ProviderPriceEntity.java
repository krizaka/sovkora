package com.krizaka.sovkora.engine.interceptors.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** JPA entity of {@code provider_price}: what a provider charges for one model. */
@Entity
@Table(name = "provider_price")
class ProviderPriceEntity {

  @Id
  @Column(name = "model_key", updatable = false, nullable = false)
  private String modelKey;

  @Column(name = "capability", nullable = false)
  private String capability;

  @Column(name = "input_usd_per_million_tokens", nullable = false)
  private BigDecimal inputUsdPerMillionTokens;

  @Column(name = "output_usd_per_million_tokens", nullable = false)
  private BigDecimal outputUsdPerMillionTokens;

  /** For JPA. */
  protected ProviderPriceEntity() {
    // loaded by the persistence provider
  }

  String getModelKey() {
    return modelKey;
  }

  String getCapability() {
    return capability;
  }

  BigDecimal getInputUsdPerMillionTokens() {
    return inputUsdPerMillionTokens;
  }

  BigDecimal getOutputUsdPerMillionTokens() {
    return outputUsdPerMillionTokens;
  }
}
