package com.krizaka.sovkora.tools.infrastructure.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ToolsProperties} and nested configuration records. */
class ToolsPropertiesTest {

  @Test
  @DisplayName("CacheConfig stores enabled and ttl")
  void cacheConfig() {
    var cache = new ToolsProperties.CacheConfig(true, 3600L);
    assertTrue(cache.enabled());
    assertEquals(3600L, cache.ttlSeconds());
  }

  @Test
  @DisplayName("RagConfig stores all fields")
  void ragConfig() {
    var rag = new ToolsProperties.RagConfig(true, "sentence", "rag_sources");
    assertTrue(rag.enabled());
    assertEquals("sentence", rag.chunkerType());
    assertEquals("rag_sources", rag.sourceTable());
  }

  @Test
  @DisplayName("ToolConfig composes cache and rag")
  void toolConfig() {
    var cache = new ToolsProperties.CacheConfig(false, 0);
    var rag = new ToolsProperties.RagConfig(true, "paragraph", "tools_rag");
    var config = new ToolsProperties.ToolConfig(cache, rag);
    assertNotNull(config.cache());
    assertNotNull(config.rag());
  }

  @Test
  @DisplayName("ToolsProperties stores configs map")
  void toolsProperties() {
    var cache = new ToolsProperties.CacheConfig(true, 60);
    var rag = new ToolsProperties.RagConfig(false, null, null);
    var config = new ToolsProperties.ToolConfig(cache, rag);
    var props = new ToolsProperties(Map.of("poster-analyzer", config));
    assertEquals(1, props.configs().size());
    assertTrue(props.configs().containsKey("poster-analyzer"));
  }

  @Test
  @DisplayName("null configs accepted")
  void nullConfigs() {
    var props = new ToolsProperties(null);
    assertNull(props.configs());
  }
}
