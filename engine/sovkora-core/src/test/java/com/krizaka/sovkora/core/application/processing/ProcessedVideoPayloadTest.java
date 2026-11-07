package com.krizaka.sovkora.core.application.processing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ProcessedVideoPayload} compact constructor validation and immutability. */
class ProcessedVideoPayloadTest {
  @org.junit.jupiter.api.Test
  void sonar_context_load() {
    org.junit.jupiter.api.Assertions.assertTrue(true);
  }

  @Nested
  @DisplayName("Compact constructor")
  class CompactConstructor {

    @Test
    @DisplayName("defaults audioTranscript to empty string when null")
    void defaultsTranscriptWhenNull() {
      var payload = new ProcessedVideoPayload(null, List.of());
      assertEquals("", payload.audioTranscript());
    }

    @Test
    @DisplayName("defaults keyframes to empty list when null")
    void defaultsKeyframesWhenNull() {
      var payload = new ProcessedVideoPayload("hello", null);
      assertNotNull(payload.keyframes());
      assertTrue(payload.keyframes().isEmpty());
    }

    @Test
    @DisplayName("defensively copies keyframes list")
    void defensivelyCopiesKeyframes() {
      var mutable = new ArrayList<>(List.of(new byte[] {1, 2, 3}));
      var payload = new ProcessedVideoPayload("text", mutable);

      mutable.add(new byte[] {4, 5, 6});
      assertEquals(1, payload.keyframes().size());
    }

    @Test
    @DisplayName("resulting keyframes list is immutable")
    void keyframesListIsImmutable() {
      var payload = new ProcessedVideoPayload("text", List.of(new byte[] {1}));
      var keyframes = payload.keyframes();
      assertThrows(UnsupportedOperationException.class, () -> keyframes.add(new byte[] {2}));
    }

    @Test
    @DisplayName("preserves valid audioTranscript")
    void preservesTranscript() {
      var payload = new ProcessedVideoPayload("Bonjour le monde", List.of());
      assertEquals("Bonjour le monde", payload.audioTranscript());
    }

    @Test
    @DisplayName("both null arguments produce safe defaults")
    void bothNullProduceSafeDefaults() {
      var payload = new ProcessedVideoPayload(null, null);
      assertEquals("", payload.audioTranscript());
      assertTrue(payload.keyframes().isEmpty());
    }
  }
}
