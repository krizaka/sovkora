package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.*;

/** JPA Entity mapping the framework's AI models catalog table. */
@Entity
@Table(name = "orazaka_models")
public class CatalogModelEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "model_name", nullable = false, unique = true)
  private String modelName;

  @Column(name = "model_label", nullable = false)
  private String modelLabel;

  @Column(name = "category", nullable = false)
  private String category;

  @Column(name = "options")
  private String options;

  @Column(name = "is_default", nullable = false)
  private Boolean isDefault = false;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public String getModelLabel() {
    return modelLabel;
  }

  public void setModelLabel(String modelLabel) {
    this.modelLabel = modelLabel;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getOptions() {
    return options;
  }

  public void setOptions(String options) {
    this.options = options;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }

  @Column(name = "provider_name", nullable = false)
  private String providerName = "ollama";

  public String getProviderName() {
    return providerName;
  }

  public void setProviderName(String providerName) {
    this.providerName = providerName;
  }

  @Column(name = "max_steps")
  private Integer maxSteps;

  @Column(name = "recommended_fps")
  private Integer recommendedFps;

  @Column(name = "supported_hardware")
  private String supportedHardware;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  public Integer getMaxSteps() {
    return maxSteps;
  }

  public void setMaxSteps(Integer maxSteps) {
    this.maxSteps = maxSteps;
  }

  public Integer getRecommendedFps() {
    return recommendedFps;
  }

  public void setRecommendedFps(Integer recommendedFps) {
    this.recommendedFps = recommendedFps;
  }

  public String getSupportedHardware() {
    return supportedHardware;
  }

  public void setSupportedHardware(String supportedHardware) {
    this.supportedHardware = supportedHardware;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  /** The locale a speech voice speaks, declared (ADR-076); null for everything else. */
  @Column(name = "locale")
  private String locale;

  /** Whether this voice is its locale's default (ADR-076). */
  @Column(name = "locale_default", nullable = false)
  private Boolean localeDefault = false;

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public Boolean getLocaleDefault() {
    return localeDefault;
  }

  public void setLocaleDefault(Boolean localeDefault) {
    this.localeDefault = localeDefault;
  }

  /**
   * {@code local} (the operator's hardware) or {@code provider} (a hosted API); null if unstated.
   */
  @Column(name = "runs_on")
  private String runsOn;

  /** {@code available}, {@code planned} or {@code internal}: only available models are offered. */
  @Column(name = "status", nullable = false)
  private String status = "available";

  /** Typical seconds for one run, measured on this setup; null while unmeasured. */
  @Column(name = "typical_seconds")
  private Integer typicalSeconds;

  /** What one run costs in US cents, null when it is free (the operator's own hardware). */
  @Column(name = "price_cents")
  private Integer priceCents;

  public String getRunsOn() {
    return runsOn;
  }

  public void setRunsOn(String runsOn) {
    this.runsOn = runsOn;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getTypicalSeconds() {
    return typicalSeconds;
  }

  public void setTypicalSeconds(Integer typicalSeconds) {
    this.typicalSeconds = typicalSeconds;
  }

  public Integer getPriceCents() {
    return priceCents;
  }

  public void setPriceCents(Integer priceCents) {
    this.priceCents = priceCents;
  }
}
