package com.krizaka.sovkora.persistence.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserMcpServerDtoTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Test
  void validConstruction_setsAllFields() {
    Instant now = Instant.now(FIXED_CLOCK);
    var dto =
        new UserMcpServerDto(1, "user-1", "My MCP", "http://localhost:3000", "token", true, now);
    assertEquals(1, dto.id());
    assertEquals("user-1", dto.userId());
    assertEquals("My MCP", dto.label());
    assertEquals("http://localhost:3000", dto.url());
    assertEquals("token", dto.authToken());
    assertTrue(dto.enabled());
    assertEquals(now, dto.createdAt());
  }

  @Test
  void nullUserId_throws() {
    assertThrows(
        NullPointerException.class,
        () -> new UserMcpServerDto(1, null, "label", "url", null, null, null));
  }

  @Test
  void nullLabel_throws() {
    assertThrows(
        NullPointerException.class,
        () -> new UserMcpServerDto(1, "user", null, "url", null, null, null));
  }

  @Test
  void nullUrl_throws() {
    assertThrows(
        NullPointerException.class,
        () -> new UserMcpServerDto(1, "user", "label", null, null, null, null));
  }

  @Test
  void nullEnabled_defaultsToTrue() {
    var dto = new UserMcpServerDto(1, "user", "label", "url", null, null, null);
    assertTrue(dto.enabled());
  }
}
