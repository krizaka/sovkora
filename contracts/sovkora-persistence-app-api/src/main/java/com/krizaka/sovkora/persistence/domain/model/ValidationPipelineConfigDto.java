package com.krizaka.sovkora.persistence.domain.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistence DTO for a validation-pipeline configuration row. {@code stepType} stays a plain
 * string at the persistence boundary (§1.2); the router maps it to/from the core enum.
 */
public record ValidationPipelineConfigDto(
    UUID id,
    String stepType,
    boolean enabled,
    int executionOrder,
    Map<String, Object> configurationPayload) {
  public ValidationPipelineConfigDto {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(stepType, "stepType must not be null");
    configurationPayload =
        configurationPayload != null ? Map.copyOf(configurationPayload) : Map.of();
  }
}
