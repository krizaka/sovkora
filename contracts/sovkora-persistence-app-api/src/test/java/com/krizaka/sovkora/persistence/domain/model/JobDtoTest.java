package com.krizaka.sovkora.persistence.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobDtoTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Test
  void validConstruction_setsAllFields() {
    Instant now = Instant.now(FIXED_CLOCK);
    var dto =
        new JobDto(
            "job-1",
            "user-1",
            "video_gen",
            "PENDING",
            Map.of("prompt", "test"),
            Map.of("url", "http://result"),
            null,
            now,
            now);
    assertEquals("job-1", dto.id());
    assertEquals("user-1", dto.userId());
    assertEquals("video_gen", dto.featureKey());
    assertEquals("PENDING", dto.status());
    assertEquals(Map.of("prompt", "test"), dto.payload());
    assertEquals(Map.of("url", "http://result"), dto.result());
    assertNull(dto.errorMessage());
    assertEquals(now, dto.createdAt());
    assertEquals(now, dto.updatedAt());
  }

  @Test
  void nullId_throws() {
    Map<String, Object> emptyMap = Map.of();
    var now = Instant.now(FIXED_CLOCK);
    assertThrows(
        NullPointerException.class,
        () -> new JobDto(null, "user", "feat", "PENDING", emptyMap, null, null, now, now));
  }

  @Test
  void blankId_throws() {
    Map<String, Object> emptyMap = Map.of();
    var now = Instant.now(FIXED_CLOCK);
    assertThrows(
        IllegalArgumentException.class,
        () -> new JobDto("  ", "user", "feat", "PENDING", emptyMap, null, null, now, now));
  }

  @Test
  void nullFeatureKey_throws() {
    Map<String, Object> emptyMap = Map.of();
    var now = Instant.now(FIXED_CLOCK);
    assertThrows(
        NullPointerException.class,
        () -> new JobDto("id", "user", null, "PENDING", emptyMap, null, null, now, now));
  }

  @Test
  void blankFeatureKey_throws() {
    Map<String, Object> emptyMap = Map.of();
    var now = Instant.now(FIXED_CLOCK);
    assertThrows(
        IllegalArgumentException.class,
        () -> new JobDto("id", "user", "  ", "PENDING", emptyMap, null, null, now, now));
  }

  @Test
  void nullPayload_defaultsToEmptyMap() {
    var dto =
        new JobDto(
            "id",
            "user",
            "feat",
            "PENDING",
            null,
            null,
            null,
            Instant.now(FIXED_CLOCK),
            Instant.now(FIXED_CLOCK));
    assertNotNull(dto.payload());
    assertTrue(dto.payload().isEmpty());
  }

  @Test
  void nullResult_defaultsToEmptyMap() {
    var dto =
        new JobDto(
            "id",
            "user",
            "feat",
            "PENDING",
            Map.of(),
            null,
            null,
            Instant.now(FIXED_CLOCK),
            Instant.now(FIXED_CLOCK));
    assertNotNull(dto.result());
    assertTrue(dto.result().isEmpty());
  }
}
