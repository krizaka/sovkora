package com.krizaka.sovkora.persistence.api.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable DTO representing a model definition from the catalog. Enforces compact constructor
 * validation per ERR-106.
 */
public record CatalogModelDto(
    Integer id,
    String modelName,
    String modelLabel,
    String category,
    String options,
    Boolean isDefault,
    String providerName,
    Integer maxSteps,
    Integer recommendedFps,
    String supportedHardware,
    String description,
    String runsOn,
    String status,
    Integer typicalSeconds,
    Integer priceCents)
    implements Serializable {

  /** Hosted by a provider (a cloud API), as opposed to the operator's own hardware. */
  public static final String RUNS_ON_PROVIDER = "provider";

  /** The only status a user may be offered a model in (the others are planned or internal). */
  public static final String STATUS_AVAILABLE = "available";

  public CatalogModelDto {
    Objects.requireNonNull(modelName, "modelName cannot be null");
    if (modelName.isBlank()) {
      throw new IllegalArgumentException("modelName cannot be blank");
    }
    Objects.requireNonNull(modelLabel, "modelLabel cannot be null");
    if (modelLabel.isBlank()) {
      throw new IllegalArgumentException("modelLabel cannot be blank");
    }
    Objects.requireNonNull(category, "category cannot be null");
    if (category.isBlank()) {
      throw new IllegalArgumentException("category cannot be blank");
    }
    if (isDefault == null) {
      isDefault = Boolean.FALSE;
    }
    if (providerName == null || providerName.isBlank()) {
      providerName = "ollama";
    }
    if (status == null || status.isBlank()) {
      status = STATUS_AVAILABLE;
    }
    if (typicalSeconds != null && typicalSeconds < 0) {
      throw new IllegalArgumentException("typicalSeconds cannot be negative");
    }
    if (priceCents != null && priceCents < 0) {
      throw new IllegalArgumentException("priceCents cannot be negative");
    }
  }

  /** Whether a provider (a cloud API) serves this model rather than the operator's hardware. */
  public boolean isHosted() {
    return RUNS_ON_PROVIDER.equals(runsOn);
  }

  /** Whether the catalogue publishes this model to users ({@code status = available}). */
  public boolean isOffered() {
    return STATUS_AVAILABLE.equals(status);
  }

  /** Constructor without the user-facing facts (runsOn, status, typical time, price). */
  public CatalogModelDto(
      Integer id,
      String modelName,
      String modelLabel,
      String category,
      String options,
      Boolean isDefault,
      String providerName,
      Integer maxSteps,
      Integer recommendedFps,
      String supportedHardware,
      String description) {
    this(
        id,
        modelName,
        modelLabel,
        category,
        options,
        isDefault,
        providerName,
        maxSteps,
        recommendedFps,
        supportedHardware,
        description,
        null,
        null,
        null,
        null);
  }

  /** Backwards compatible constructor with 6 parameters. */
  public CatalogModelDto(
      Integer id,
      String modelName,
      String modelLabel,
      String category,
      String options,
      Boolean isDefault) {
    this(id, modelName, modelLabel, category, options, isDefault, "ollama", null, null, null);
  }

  /** Backwards compatible constructor with 7 parameters. */
  public CatalogModelDto(
      Integer id,
      String modelName,
      String modelLabel,
      String category,
      String options,
      Boolean isDefault,
      String providerName) {
    this(id, modelName, modelLabel, category, options, isDefault, providerName, null, null, null);
  }

  /** Backwards compatible constructor without a description (pre-description catalog rows). */
  public CatalogModelDto(
      Integer id,
      String modelName,
      String modelLabel,
      String category,
      String options,
      Boolean isDefault,
      String providerName,
      Integer maxSteps,
      Integer recommendedFps,
      String supportedHardware) {
    this(
        id,
        modelName,
        modelLabel,
        category,
        options,
        isDefault,
        providerName,
        maxSteps,
        recommendedFps,
        supportedHardware,
        null);
  }
}
