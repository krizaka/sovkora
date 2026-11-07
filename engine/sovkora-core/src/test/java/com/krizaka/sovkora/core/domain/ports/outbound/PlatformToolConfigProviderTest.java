package com.krizaka.sovkora.core.domain.ports.outbound;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlatformToolConfigProviderTest {

  @Test
  void platformToolConfigRecordGettersAndConstructors() {
    PlatformToolConfigProvider.PlatformToolConfig config =
        new PlatformToolConfigProvider.PlatformToolConfig(
            1, "tool-123", true, 300, false, "paragraph", "sources");

    assertEquals(1, config.id());
    assertEquals("tool-123", config.toolId());
    assertTrue(config.cacheEnabled());
    assertEquals(300, config.cacheTtlSeconds());
    assertFalse(config.ragEnabled());
    assertEquals("paragraph", config.chunkerType());
    assertEquals("sources", config.sourceTable());
  }

  @Test
  void platformToolConfigEqualsHashCodeToString() {
    PlatformToolConfigProvider.PlatformToolConfig config1 =
        new PlatformToolConfigProvider.PlatformToolConfig(
            1, "tool-123", true, 300, false, "paragraph", "sources");

    PlatformToolConfigProvider.PlatformToolConfig config2 =
        new PlatformToolConfigProvider.PlatformToolConfig(
            1, "tool-123", true, 300, false, "paragraph", "sources");

    PlatformToolConfigProvider.PlatformToolConfig config3 =
        new PlatformToolConfigProvider.PlatformToolConfig(
            2, "tool-456", false, 600, true, "sentence", "other");

    assertEquals(config1, config2);
    assertNotEquals(config1, config3);
    assertEquals(config1.hashCode(), config2.hashCode());
    assertNotEquals(config1.hashCode(), config3.hashCode());
    assertNotNull(config1.toString());
  }
}
