package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import com.krizaka.sovkora.persistence.domain.model.ValidationPipelineConfigDto;

/**
 * Package-private static mapper: persistence validation-pipeline DTO ↔ core domain record.
 *
 * <p>Handles the {@code String ↔ ValidationStepType} conversion at the boundary — persistence keeps
 * plain strings (§1.2), the core uses the enum [ERR-107].
 */
final class ValidationPipelineConfigMapper {

  private ValidationPipelineConfigMapper() {}

  static ValidationPipelineConfiguration toRecord(ValidationPipelineConfigDto dto) {
    return new ValidationPipelineConfiguration(
        dto.id(),
        ValidationStepType.valueOf(dto.stepType()),
        dto.enabled(),
        dto.executionOrder(),
        dto.configurationPayload());
  }

  static ValidationPipelineConfigDto toDto(ValidationPipelineConfiguration config) {
    return new ValidationPipelineConfigDto(
        config.id(),
        config.stepType().name(),
        config.enabled(),
        config.executionOrder(),
        config.configurationPayload());
  }
}
