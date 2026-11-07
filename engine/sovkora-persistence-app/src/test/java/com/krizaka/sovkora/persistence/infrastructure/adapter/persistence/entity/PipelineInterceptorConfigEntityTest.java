package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PipelineInterceptorConfigEntityTest {

  @Test
  void setAndGetInterceptorKey() {
    var entity = new PipelineInterceptorConfigEntity();
    entity.setInterceptorKey("refiner");
    assertEquals("refiner", entity.getInterceptorKey());
  }

  @Test
  void setAndGetDisplayLabel() {
    var entity = new PipelineInterceptorConfigEntity();
    entity.setDisplayLabel("Refiner Interceptor");
    assertEquals("Refiner Interceptor", entity.getDisplayLabel());
  }

  @Test
  void setAndGetExecutionOrder() {
    var entity = new PipelineInterceptorConfigEntity();
    entity.setExecutionOrder(6);
    assertEquals(6, entity.getExecutionOrder());
  }

  @Test
  void setAndGetIsEnabled() {
    var entity = new PipelineInterceptorConfigEntity();
    entity.setIsEnabled(false);
    assertFalse(entity.getIsEnabled());
  }

  @Test
  void setAndGetDescription() {
    var entity = new PipelineInterceptorConfigEntity();
    entity.setDescription("Test description");
    assertEquals("Test description", entity.getDescription());
  }

  @Test
  void defaultValues() {
    var entity = new PipelineInterceptorConfigEntity();
    assertEquals(0, entity.getExecutionOrder());
    assertTrue(entity.getIsEnabled());
    assertNotNull(entity.getCreatedAt());
    assertNotNull(entity.getUpdatedAt());
  }
}
