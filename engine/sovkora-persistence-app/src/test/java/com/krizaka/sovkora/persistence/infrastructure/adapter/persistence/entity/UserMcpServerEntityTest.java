package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UserMcpServerEntity} getter/setter coverage. */
class UserMcpServerEntityTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Test
  void defaultConstructor_setsDefaults() {
    UserMcpServerEntity entity = new UserMcpServerEntity();
    assertNull(entity.getId());
    assertTrue(entity.getEnabled());
    assertNotNull(entity.getCreatedAt());
  }

  @Test
  void settersAndGetters_roundTrip() {
    UserMcpServerEntity entity = new UserMcpServerEntity();
    Instant now = Instant.now(FIXED_CLOCK);

    entity.setId(42);
    entity.setUserId("user-xyz");
    entity.setLabel("My MCP Server");
    entity.setUrl("https://mcp.orazaka.io");
    entity.setAuthToken("bearer-token-123");
    entity.setEnabled(false);
    entity.setCreatedAt(now);

    assertEquals(42, entity.getId());
    assertEquals("user-xyz", entity.getUserId());
    assertEquals("My MCP Server", entity.getLabel());
    assertEquals("https://mcp.orazaka.io", entity.getUrl());
    assertEquals("bearer-token-123", entity.getAuthToken());
    assertFalse(entity.getEnabled());
    assertEquals(now, entity.getCreatedAt());
  }
}
