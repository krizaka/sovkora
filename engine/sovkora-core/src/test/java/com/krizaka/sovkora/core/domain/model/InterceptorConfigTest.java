package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InterceptorConfigTest {

  @Test
  void validConstruction() {
    var config = new InterceptorConfig("MEMORY", "Memory", 5, true, "Memory interceptor");
    assertEquals("MEMORY", config.interceptorKey());
    assertEquals("Memory", config.displayLabel());
    assertEquals(5, config.executionOrder());
    assertTrue(config.enabled());
    assertEquals("Memory interceptor", config.description());
  }

  @Test
  void nullInterceptorKey_throws() {
    assertThrows(
        NullPointerException.class, () -> new InterceptorConfig(null, "label", 1, true, "desc"));
  }

  @Test
  void nullDisplayLabel_throws() {
    assertThrows(
        NullPointerException.class, () -> new InterceptorConfig("key", null, 1, true, "desc"));
  }

  @Test
  void nullDescription_allowed() {
    var config = new InterceptorConfig("key", "label", 1, true, null);
    assertNull(config.description());
  }
}
