package com.krizaka.sovkora.persistence.bridge;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.sovkora.core.domain.model.InterceptorConfig;
import com.krizaka.sovkora.persistence.domain.model.InterceptorConfigDto;
import org.junit.jupiter.api.Test;

class PipelineInterceptorConfigMapperTest {

  @Test
  void toRecord_mapsAllFields() {
    var dto =
        new InterceptorConfigDto("refiner", "Refiner Interceptor", 6, true, "Refines queries");
    InterceptorConfig config = PipelineInterceptorConfigMapper.toRecord(dto);
    assertEquals("refiner", config.interceptorKey());
    assertEquals("Refiner Interceptor", config.displayLabel());
    assertEquals(6, config.executionOrder());
    assertTrue(config.enabled());
    assertEquals("Refines queries", config.description());
  }

  @Test
  void toDto_mapsAllFields() {
    var config = new InterceptorConfig("refiner", "Refiner", 6, true, "desc");
    InterceptorConfigDto dto = PipelineInterceptorConfigMapper.toDto(config);
    assertEquals("refiner", dto.interceptorKey());
    assertEquals("Refiner", dto.displayLabel());
    assertEquals(6, dto.executionOrder());
    assertTrue(dto.enabled());
    assertEquals("desc", dto.description());
  }

  @Test
  void roundTrip_preservesData() {
    var original = new InterceptorConfig("memory", "Memory", 5, false, "Conversation memory");
    assertEquals(
        original,
        PipelineInterceptorConfigMapper.toRecord(PipelineInterceptorConfigMapper.toDto(original)));
  }
}
