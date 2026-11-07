package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA Entity mapping the {@code orazaka_runtime_config} table (ADR-031).
 *
 * <p>Single source of truth for <b>dynamic runtime</b> behaviour toggles/tuning read AFTER startup
 * (an admin changes these live, no redeploy) — e.g. {@code rag.enabled}, {@code rag.top-k}, {@code
 * rate-limit.enabled}. NOT for bootstrap/infra config, which stays in {@code application.yml}.
 */
@Entity
@Table(name = "orazaka_runtime_config")
public class RuntimeConfigEntity {

  @Id
  @Column(name = "config_key", length = 120)
  private String configKey;

  @Column(name = "config_value", nullable = false)
  private String configValue;

  @Column(name = "value_type", nullable = false, length = 20)
  private String valueType;

  @Column(name = "description")
  private String description;

  public String getConfigKey() {
    return configKey;
  }

  public void setConfigKey(String configKey) {
    this.configKey = configKey;
  }

  public String getConfigValue() {
    return configValue;
  }

  public void setConfigValue(String configValue) {
    this.configValue = configValue;
  }

  public String getValueType() {
    return valueType;
  }

  public void setValueType(String valueType) {
    this.valueType = valueType;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
