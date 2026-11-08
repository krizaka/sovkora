package com.krizaka.sovkora.persistence.api.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlatformMcpServerDtoTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Test
  void validConstruction_setsAllFields() {
    Instant now = Instant.now(FIXED_CLOCK);
    var dto =
        new PlatformMcpServerDto(
            1, "GitHub MCP", "STDIO", "http://localhost", "node", "args", "token", true, now);
    assertEquals(1, dto.id());
    assertEquals("GitHub MCP", dto.label());
    assertEquals("STDIO", dto.transportType());
    assertEquals("http://localhost", dto.url());
    assertEquals("node", dto.command());
    assertEquals("args", dto.args());
    assertEquals("token", dto.authToken());
    assertTrue(dto.enabled());
    assertEquals(now, dto.createdAt());
  }

  @Test
  void nullLabel_throws() {
    var now = Instant.now(FIXED_CLOCK);
    assertThrows(
        NullPointerException.class,
        () -> new PlatformMcpServerDto(1, null, "SSE", "url", null, null, null, true, now));
  }

  @Test
  void nullTransportType_throws() {
    var now = Instant.now(FIXED_CLOCK);
    assertThrows(
        NullPointerException.class,
        () -> new PlatformMcpServerDto(1, "label", null, "url", null, null, null, true, now));
  }

  @Test
  void nullEnabled_defaultsToTrue() {
    var dto =
        new PlatformMcpServerDto(
            1, "label", "SSE", "url", null, null, null, null, Instant.now(FIXED_CLOCK));
    assertTrue(dto.enabled());
  }

  @Test
  void optionalFieldsNullable() {
    var dto = new PlatformMcpServerDto(null, "label", "SSE", null, null, null, null, true, null);
    assertNull(dto.id());
    assertNull(dto.url());
    assertNull(dto.command());
    assertNull(dto.args());
    assertNull(dto.authToken());
    assertNull(dto.createdAt());
  }
}
