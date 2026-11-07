package com.krizaka.sovkora.persistence.bridge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import com.krizaka.sovkora.persistence.domain.model.ValidationPipelineConfigDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.ValidationPipelineManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ValidationPipelineRepositoryAdapter} (delegates to the persistence port).
 */
class ValidationPipelineRepositoryAdapterTest {

  private final ValidationPipelineManager manager = mock(ValidationPipelineManager.class);
  private final ValidationPipelineRepositoryAdapter repository =
      new ValidationPipelineRepositoryAdapter(manager);

  @Test
  @DisplayName("findAllOrderedByExecution maps DTOs to records")
  void findAllOrdered_mapsDtos() {
    when(manager.findAllOrderedByExecution())
        .thenReturn(
            List.of(
                new ValidationPipelineConfigDto(
                    UUID.randomUUID(), "STRUCTURAL_A", true, 1, Map.of()),
                new ValidationPipelineConfigDto(
                    UUID.randomUUID(), "SEMANTIC_C", false, 3, Map.of())));

    List<ValidationPipelineConfiguration> result = repository.findAllOrderedByExecution();

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().stepType()).isEqualTo(ValidationStepType.STRUCTURAL_A);
    assertThat(result.get(1).enabled()).isFalse();
  }

  @Test
  @DisplayName("save maps record → DTO and back")
  void save_mapsRoundTrip() {
    var config =
        new ValidationPipelineConfiguration(
            UUID.randomUUID(), ValidationStepType.TDR_D, true, 4, Map.of("model", "qwen"));
    when(manager.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ValidationPipelineConfiguration result = repository.save(config);

    assertThat(result.stepType()).isEqualTo(ValidationStepType.TDR_D);
    verify(manager).save(any(ValidationPipelineConfigDto.class));
  }

  @Test
  @DisplayName("saveAll delegates and maps")
  void saveAll_delegates() {
    when(manager.saveAll(anyList()))
        .thenReturn(
            List.of(
                new ValidationPipelineConfigDto(
                    UUID.randomUUID(), "STRUCTURAL_A", true, 1, Map.of())));
    List<ValidationPipelineConfiguration> result =
        repository.saveAll(
            List.of(
                new ValidationPipelineConfiguration(
                    UUID.randomUUID(), ValidationStepType.STRUCTURAL_A, true, 1, Map.of())));
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().stepType()).isEqualTo(ValidationStepType.STRUCTURAL_A);
  }

  @Test
  @DisplayName("constructor rejects null manager")
  void constructor_nullThrows() {
    assertThatThrownBy(() -> new ValidationPipelineRepositoryAdapter(null))
        .isInstanceOf(NullPointerException.class);
  }
}
