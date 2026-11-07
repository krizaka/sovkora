package com.krizaka.sovkora.engine.persistence.api.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChatSessionDtoTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Test
  void validConstruction_setsAllFields() {
    Instant now = Instant.now(FIXED_CLOCK);
    var dto = new ChatSessionDto("sess-1", "user-1", "My Chat", now);
    assertEquals("sess-1", dto.id());
    assertEquals("user-1", dto.userId());
    assertEquals("My Chat", dto.title());
    assertEquals(now, dto.updatedAt());
  }

  @Test
  void nullId_throws() {
    var now = Instant.now(FIXED_CLOCK);
    assertThrows(
        NullPointerException.class, () -> new ChatSessionDto(null, "user-1", "title", now));
  }

  @Test
  void nullUserId_throws() {
    var now = Instant.now(FIXED_CLOCK);
    assertThrows(NullPointerException.class, () -> new ChatSessionDto("id", null, "title", now));
  }

  @Test
  void nullTitle_throws() {
    var now = Instant.now(FIXED_CLOCK);
    assertThrows(NullPointerException.class, () -> new ChatSessionDto("id", "user-1", null, now));
  }

  @Test
  void nullUpdatedAt_throws() {
    assertThrows(
        NullPointerException.class, () -> new ChatSessionDto("id", "user-1", "title", null));
  }

  @Test
  void equalsAndHashCode() {
    Instant now = Instant.now(FIXED_CLOCK);
    var a = new ChatSessionDto("id", "user", "title", now);
    var b = new ChatSessionDto("id", "user", "title", now);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }
}
