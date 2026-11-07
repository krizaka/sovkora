package com.krizaka.sovkora.engine.persistence.api.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlatformToolConfigDtoTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Test
  void validConstruction_setsAllFields() {
    var dto =
        new PlatformToolConfigDto(
            1, "tool-1", true, 600, false, "FIXED", "custom_table", Instant.now(FIXED_CLOCK));
    assertEquals(1, dto.id());
    assertEquals("tool-1", dto.toolId());
    assertTrue(dto.cacheEnabled());
    assertEquals(600, dto.cacheTtlSeconds());
    assertFalse(dto.ragEnabled());
    assertEquals("FIXED", dto.chunkerType());
    assertEquals("custom_table", dto.sourceTable());
  }

  @Test
  void nullToolId_throws() {
    var now = Instant.now(FIXED_CLOCK);
    assertThrows(
        NullPointerException.class,
        () -> new PlatformToolConfigDto(1, null, true, 600, true, "MD", "table", now));
  }

  @Test
  void nullCacheEnabled_defaultsToTrue() {
    var dto =
        new PlatformToolConfigDto(
            1, "tool-1", null, 600, true, "MD", "table", Instant.now(FIXED_CLOCK));
    assertTrue(dto.cacheEnabled());
  }

  @Test
  void nullCacheTtl_defaultsTo3600() {
    var dto =
        new PlatformToolConfigDto(
            1, "tool-1", true, null, true, "MD", "table", Instant.now(FIXED_CLOCK));
    assertEquals(3600, dto.cacheTtlSeconds());
  }

  @Test
  void nullRagEnabled_defaultsToTrue() {
    var dto =
        new PlatformToolConfigDto(
            1, "tool-1", true, 600, null, "MD", "table", Instant.now(FIXED_CLOCK));
    assertTrue(dto.ragEnabled());
  }

  @Test
  void nullChunkerType_defaultsToMarkdown() {
    var dto =
        new PlatformToolConfigDto(
            1, "tool-1", true, 600, true, null, "table", Instant.now(FIXED_CLOCK));
    assertEquals("MARKDOWN_CHUNKERS", dto.chunkerType());
  }

  @Test
  void nullSourceTable_defaultsToRagSource() {
    var dto =
        new PlatformToolConfigDto(
            1, "tool-1", true, 600, true, "MD", null, Instant.now(FIXED_CLOCK));
    assertEquals("orazaka_tools_rag_source", dto.sourceTable());
  }

  @Test
  void allNullDefaults_appliedCorrectly() {
    var dto = new PlatformToolConfigDto(null, "tool-1", null, null, null, null, null, null);
    assertTrue(dto.cacheEnabled());
    assertEquals(3600, dto.cacheTtlSeconds());
    assertTrue(dto.ragEnabled());
    assertEquals("MARKDOWN_CHUNKERS", dto.chunkerType());
    assertEquals("orazaka_tools_rag_source", dto.sourceTable());
  }
}
