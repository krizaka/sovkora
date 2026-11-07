package com.krizaka.sovkora.core.application.processing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ProcessedAudioPayload} — null-safety compact constructor. */
class ProcessedAudioPayloadTest {

  @Test
  @DisplayName("null transcript defaults to empty string")
  void nullTranscriptDefaults() {
    var payload = new ProcessedAudioPayload(null);
    assertEquals("", payload.transcript());
  }

  @Test
  @DisplayName("valid transcript preserved")
  void validTranscript() {
    var payload = new ProcessedAudioPayload("Hello, world!");
    assertEquals("Hello, world!", payload.transcript());
  }
}
