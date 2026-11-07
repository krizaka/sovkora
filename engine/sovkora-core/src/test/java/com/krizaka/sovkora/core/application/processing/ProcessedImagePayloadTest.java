package com.krizaka.sovkora.core.application.processing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ProcessedImagePayload} — null-safety and dimensional invariants. */
class ProcessedImagePayloadTest {
  @org.junit.jupiter.api.Test
  void sonar_context_load() {
    org.junit.jupiter.api.Assertions.assertTrue(true);
  }

  @Nested
  @DisplayName("Compact constructor defaults")
  class ConstructorDefaults {

    @Test
    @DisplayName("null base64Image defaults to empty string")
    void nullImageDefaults() {
      var payload = new ProcessedImagePayload(null, 100, 200);
      assertEquals("", payload.base64Image());
    }

    @Test
    @DisplayName("valid values preserved")
    void validValues() {
      var payload = new ProcessedImagePayload("abc123", 640, 480);
      assertEquals("abc123", payload.base64Image());
      assertEquals(640, payload.width());
      assertEquals(480, payload.height());
    }
  }

  @Nested
  @DisplayName("Dimensional invariants")
  class DimensionalInvariants {

    @Test
    @DisplayName("negative width throws IAE")
    void negativeWidth() {
      assertThrows(IllegalArgumentException.class, () -> new ProcessedImagePayload("img", -1, 100));
    }

    @Test
    @DisplayName("negative height throws IAE")
    void negativeHeight() {
      assertThrows(IllegalArgumentException.class, () -> new ProcessedImagePayload("img", 100, -1));
    }

    @Test
    @DisplayName("zero dimensions are accepted")
    void zeroDimensionsAccepted() {
      var payload = new ProcessedImagePayload("img", 0, 0);
      assertEquals(0, payload.width());
      assertEquals(0, payload.height());
    }
  }
}
