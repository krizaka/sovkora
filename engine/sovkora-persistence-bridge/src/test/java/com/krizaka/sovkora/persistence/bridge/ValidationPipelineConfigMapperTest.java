package com.krizaka.sovkora.persistence.bridge;

import static org.assertj.core.api.Assertions.*;

import com.krizaka.sovkora.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import com.krizaka.sovkora.persistence.domain.model.ValidationPipelineConfigDto;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ValidationPipelineConfigMapper} (persistence DTO ↔ core record). */
class ValidationPipelineConfigMapperTest {

  @Test
  @DisplayName("toRecord maps all fields incl. String → enum")
  void toRecord_mapsAllFields() {
    UUID id = UUID.randomUUID();
    var dto =
        new ValidationPipelineConfigDto(id, "STRUCTURAL_A", true, 1, Map.of("schemaStrict", true));

    ValidationPipelineConfiguration config = ValidationPipelineConfigMapper.toRecord(dto);

    assertThat(config.id()).isEqualTo(id);
    assertThat(config.stepType()).isEqualTo(ValidationStepType.STRUCTURAL_A);
    assertThat(config.enabled()).isTrue();
    assertThat(config.executionOrder()).isEqualTo(1);
    assertThat(config.configurationPayload()).containsEntry("schemaStrict", true);
  }

  @Test
  @DisplayName("toDto maps all fields incl. enum → String")
  void toDto_mapsAllFields() {
    UUID id = UUID.randomUUID();
    var config =
        new ValidationPipelineConfiguration(
            id, ValidationStepType.SEMANTIC_C, true, 3, Map.of("debateTemperature", 0.0));

    ValidationPipelineConfigDto dto = ValidationPipelineConfigMapper.toDto(config);

    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.stepType()).isEqualTo("SEMANTIC_C");
    assertThat(dto.enabled()).isTrue();
    assertThat(dto.executionOrder()).isEqualTo(3);
    assertThat(dto.configurationPayload()).containsEntry("debateTemperature", 0.0);
  }

  @Test
  @DisplayName("round-trip preserves data")
  void roundTrip_preservesData() {
    UUID id = UUID.randomUUID();
    var original =
        new ValidationPipelineConfiguration(
            id, ValidationStepType.TDR_D, false, 4, Map.of("modelName", "qwen2.5-coder:7b"));
    assertThat(
            ValidationPipelineConfigMapper.toRecord(ValidationPipelineConfigMapper.toDto(original)))
        .isEqualTo(original);
  }

  @Test
  @DisplayName("round-trip for each step type")
  void roundTrip_eachStepType() {
    for (ValidationStepType type : ValidationStepType.values()) {
      var config =
          new ValidationPipelineConfiguration(
              UUID.randomUUID(), type, true, type.defaultOrder(), Map.of());
      var dto = ValidationPipelineConfigMapper.toDto(config);
      assertThat(dto.stepType()).isEqualTo(type.name());
      assertThat(ValidationPipelineConfigMapper.toRecord(dto).stepType()).isEqualTo(type);
    }
  }

  @Test
  @DisplayName("invalid step type string throws")
  void toRecord_invalidStepTypeThrows() {
    var dto =
        new ValidationPipelineConfigDto(UUID.randomUUID(), "INVALID_TIER", true, 99, Map.of());
    assertThatThrownBy(() -> ValidationPipelineConfigMapper.toRecord(dto))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
