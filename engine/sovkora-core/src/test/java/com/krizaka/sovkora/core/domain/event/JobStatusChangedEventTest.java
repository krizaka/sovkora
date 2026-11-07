package com.krizaka.sovkora.core.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.krizaka.sovkora.core.domain.model.job.JobInfo;
import com.krizaka.sovkora.core.domain.model.job.JobStatus;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobStatusChangedEventTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Test
  @DisplayName("event requires non-null job info")
  void validation() {
    assertThrows(NullPointerException.class, () -> new JobStatusChangedEvent(null));

    Instant now = Instant.now(FIXED_CLOCK);
    JobInfo jobInfo =
        new JobInfo(
            "job-1", "user-1", "feature-1", JobStatus.PENDING, Map.of(), Map.of(), null, now, now);

    JobStatusChangedEvent event = new JobStatusChangedEvent(jobInfo);
    assertEquals(jobInfo, event.job());
  }
}
