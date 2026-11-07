package com.krizaka.sovkora.core.domain.model.image;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What image generation is billed on, and the distinction that stops a guess becoming a bill. */
class ImageUsageTest {

  @Test
  @DisplayName("a reported generation carries every number IMAGE_STEP needs")
  void reportedGenerationIsPriceable() {
    ImageUsage usage = new ImageUsage(1, 20, 1024, 1024);

    assertTrue(usage.reported());
    assertEquals(1, usage.images());
    assertEquals(20, usage.steps());
  }

  @Test
  @DisplayName("nothing reported is not nothing consumed — it releases the hold")
  void unreportedIsNotZero() {
    assertFalse(ImageUsage.none().reported());
    assertTrue(new ImageUsage(0, 0, 0, 0).reported());
  }

  @Test
  @DisplayName("a partially reported generation is unreported — a half-priced bill is worse")
  void partialReportIsUnreported() {
    assertFalse(new ImageUsage(1, -1, 1024, 1024).reported());
    assertFalse(new ImageUsage(1, 20, -1, 1024).reported());
  }

  @Test
  @DisplayName("a count below the unreported sentinel is a programming error, not a bill")
  void negativeCountsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new ImageUsage(-2, 20, 1024, 1024));
    assertThrows(IllegalArgumentException.class, () -> new ImageUsage(1, 20, 1024, -3));
  }
}
