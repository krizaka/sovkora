package com.krizaka.sovkora.persistence.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.persistence.domain.model.ValidationPipelineConfigDto;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.ValidationPipelineConfigEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.ValidationPipelineConfigJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidationPipelineManagerImplTest {

  private final ValidationPipelineConfigJpaRepository repository =
      mock(ValidationPipelineConfigJpaRepository.class);
  private final ValidationPipelineManagerImpl manager =
      new ValidationPipelineManagerImpl(repository);

  private static ValidationPipelineConfigEntity entity(
      String stepType, Boolean enabled, int order) {
    var e = new ValidationPipelineConfigEntity();
    e.setId(UUID.randomUUID());
    e.setStepType(stepType);
    e.setIsEnabled(enabled);
    e.setExecutionOrder(order);
    e.setConfigurationPayload(Map.of());
    return e;
  }

  @Test
  void findAllOrdered_mapsToDtos() {
    when(repository.findAllByOrderByExecutionOrderAsc())
        .thenReturn(List.of(entity("STRUCTURAL_A", true, 1)));
    List<ValidationPipelineConfigDto> result = manager.findAllOrderedByExecution();
    assertEquals(1, result.size());
    assertEquals("STRUCTURAL_A", result.getFirst().stepType());
  }

  @Test
  void findAll_nullPayload_mapsToEmpty() {
    var e = entity("SANDBOX_B", true, 2);
    e.setConfigurationPayload(null);
    when(repository.findAllByOrderByExecutionOrderAsc()).thenReturn(List.of(e));
    assertTrue(manager.findAllOrderedByExecution().getFirst().configurationPayload().isEmpty());
  }

  @Test
  void save_new_createsEntity() {
    var dto =
        new ValidationPipelineConfigDto(UUID.randomUUID(), "TDR_D", true, 4, Map.of("m", "qwen"));
    when(repository.findByStepType("TDR_D")).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    assertEquals("TDR_D", manager.save(dto).stepType());
    verify(repository).save(any());
  }

  @Test
  void save_existing_updatesEntity() {
    var existing = entity("STRUCTURAL_A", true, 1);
    when(repository.findByStepType("STRUCTURAL_A")).thenReturn(Optional.of(existing));
    when(repository.save(existing)).thenReturn(existing);
    manager.save(
        new ValidationPipelineConfigDto(
            UUID.randomUUID(), "STRUCTURAL_A", false, 5, Map.of("new", "value")));
    assertFalse(existing.getIsEnabled());
    assertEquals(5, existing.getExecutionOrder());
    assertTrue(existing.getConfigurationPayload().containsKey("new"));
  }

  @Test
  void saveAll_savesEach() {
    when(repository.findByStepType(anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    var result =
        manager.saveAll(
            List.of(
                new ValidationPipelineConfigDto(
                    UUID.randomUUID(), "STRUCTURAL_A", true, 1, Map.of()),
                new ValidationPipelineConfigDto(
                    UUID.randomUUID(), "SEMANTIC_C", false, 3, Map.of())));
    assertEquals(2, result.size());
    verify(repository, times(2)).save(any());
  }
}
