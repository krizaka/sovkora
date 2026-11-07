package com.krizaka.sovkora.persistence.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.persistence.domain.model.InterceptorConfigDto;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PipelineInterceptorConfigEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.PipelineInterceptorConfigRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PipelineConfigManagerImplTest {

  private final PipelineInterceptorConfigRepository repository =
      mock(PipelineInterceptorConfigRepository.class);
  private final PipelineConfigManagerImpl manager = new PipelineConfigManagerImpl(repository);

  private static PipelineInterceptorConfigEntity entity(String key, Boolean enabled, int order) {
    var e = new PipelineInterceptorConfigEntity();
    e.setInterceptorKey(key);
    e.setDisplayLabel("Label");
    e.setExecutionOrder(order);
    e.setIsEnabled(enabled);
    e.setDescription("desc");
    return e;
  }

  @Test
  void findAllOrdered_mapsToDtos() {
    when(repository.findAllByOrderByExecutionOrderAscIdAsc())
        .thenReturn(List.of(entity("MEMORY", true, 5)));
    List<InterceptorConfigDto> result = manager.findAllOrdered();
    assertEquals(1, result.size());
    assertEquals("MEMORY", result.getFirst().interceptorKey());
    assertTrue(result.getFirst().enabled());
  }

  @Test
  void findAllOrdered_nullIsEnabled_mapsToFalse() {
    when(repository.findAllByOrderByExecutionOrderAscIdAsc())
        .thenReturn(List.of(entity("MEMORY", null, 5)));
    assertFalse(manager.findAllOrdered().getFirst().enabled());
  }

  @Test
  void save_new_createsEntity() {
    var dto = new InterceptorConfigDto("ROUTER", "Router", 7, true, "Router");
    when(repository.findByInterceptorKey("ROUTER")).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    assertEquals("ROUTER", manager.save(dto).interceptorKey());
    verify(repository).save(any());
  }

  @Test
  void save_existing_updatesEntity() {
    var existing = entity("MEMORY", false, 1);
    when(repository.findByInterceptorKey("MEMORY")).thenReturn(Optional.of(existing));
    when(repository.save(existing)).thenReturn(existing);
    manager.save(new InterceptorConfigDto("MEMORY", "New", 5, true, "Updated"));
    assertEquals("New", existing.getDisplayLabel());
    assertEquals(5, existing.getExecutionOrder());
    assertTrue(existing.getIsEnabled());
  }

  @Test
  void resetToDefaults_bulkDeletesThenSaves() {
    manager.resetToDefaults(List.of(new InterceptorConfigDto("ROUTER", "Router", 7, true, "d")));
    verify(repository).deleteAllInBatch();
    verify(repository).saveAll(anyList());
  }
}
