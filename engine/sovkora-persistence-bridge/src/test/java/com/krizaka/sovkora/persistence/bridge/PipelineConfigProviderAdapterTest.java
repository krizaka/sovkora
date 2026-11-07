package com.krizaka.sovkora.persistence.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.InterceptorConfig;
import com.krizaka.sovkora.persistence.domain.model.InterceptorConfigDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.PipelineConfigManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineConfigProviderAdapterTest {

  private final PipelineConfigManager manager = mock(PipelineConfigManager.class);
  private final PipelineConfigProviderAdapter provider = new PipelineConfigProviderAdapter(manager);

  @Test
  void findAllOrdered_mapsDtosToRecords() {
    when(manager.findAllOrdered())
        .thenReturn(List.of(new InterceptorConfigDto("MEMORY", "Memory", 5, true, "Memory")));
    List<InterceptorConfig> result = provider.findAllOrdered();
    assertEquals(1, result.size());
    assertEquals("MEMORY", result.getFirst().interceptorKey());
    assertEquals(5, result.getFirst().executionOrder());
    assertTrue(result.getFirst().enabled());
  }

  @Test
  void findAllOrdered_emptyList() {
    when(manager.findAllOrdered()).thenReturn(List.of());
    assertTrue(provider.findAllOrdered().isEmpty());
  }

  @Test
  void save_mapsRecordToDtoAndBack() {
    var config = new InterceptorConfig("ROUTER", "Router", 7, true, "Router interceptor");
    when(manager.save(any())).thenAnswer(inv -> inv.getArgument(0));
    InterceptorConfig result = provider.save(config);
    assertEquals("ROUTER", result.interceptorKey());
    verify(manager).save(any(InterceptorConfigDto.class));
  }

  @Test
  void saveAll_delegatesAndMaps() {
    when(manager.saveAll(anyList()))
        .thenReturn(List.of(new InterceptorConfigDto("ROUTER", "Router", 7, true, "d")));
    List<InterceptorConfig> result =
        provider.saveAll(List.of(new InterceptorConfig("ROUTER", "Router", 7, true, "d")));
    assertEquals(1, result.size());
    assertEquals("ROUTER", result.getFirst().interceptorKey());
  }

  @Test
  void resetToDefaults_delegates() {
    provider.resetToDefaults(List.of(new InterceptorConfig("ROUTER", "Router", 7, true, "d")));
    verify(manager).resetToDefaults(anyList());
  }

  @Test
  void constructor_nullManager_throws() {
    assertThrows(NullPointerException.class, () -> new PipelineConfigProviderAdapter(null));
  }
}
