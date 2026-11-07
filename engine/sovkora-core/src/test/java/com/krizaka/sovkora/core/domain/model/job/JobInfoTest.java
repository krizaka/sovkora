package com.krizaka.sovkora.core.domain.model.job;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobInfoTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  private final Instant now = Instant.now(FIXED_CLOCK);

  @Test
  void validConstruction() {
    var info =
        new JobInfo(
            "job-1",
            "user-1",
            "video",
            JobStatus.PENDING,
            Map.of("prompt", "hello"),
            Map.of(),
            null,
            now,
            now);
    assertEquals("job-1", info.id());
    assertEquals("user-1", info.userId());
    assertEquals("video", info.featureKey());
    assertEquals(JobStatus.PENDING, info.status());
    assertEquals("hello", info.payload().get("prompt"));
  }

  @Test
  void nullId_throws() {
    assertThrows(
        NullPointerException.class,
        () -> new JobInfo(null, "user", "video", JobStatus.PENDING, null, null, null, now, now));
  }

  @Test
  void blankId_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new JobInfo("  ", "user", "video", JobStatus.PENDING, null, null, null, now, now));
  }

  @Test
  void nullFeatureKey_throws() {
    assertThrows(
        NullPointerException.class,
        () -> new JobInfo("id", "user", null, JobStatus.PENDING, null, null, null, now, now));
  }

  @Test
  void nullStatus_throws() {
    assertThrows(
        NullPointerException.class,
        () -> new JobInfo("id", "user", "video", null, null, null, null, now, now));
  }

  @Test
  void nullPayload_defaultsToEmptyMap() {
    var info = new JobInfo("id", "user", "video", JobStatus.PENDING, null, null, null, now, now);
    assertTrue(info.payload().isEmpty());
    assertTrue(info.result().isEmpty());
  }
}
