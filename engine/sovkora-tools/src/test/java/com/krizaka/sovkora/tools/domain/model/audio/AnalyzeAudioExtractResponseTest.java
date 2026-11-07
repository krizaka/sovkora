package com.krizaka.sovkora.tools.domain.model.audio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AnalyzeAudioExtractResponse} — compact constructor validation. */
class AnalyzeAudioExtractResponseTest {

  @Test
  @DisplayName("null analysis throws NPE")
  void nullAnalysis() {
    assertThrows(NullPointerException.class, () -> new AnalyzeAudioExtractResponse(null, true));
  }

  @Test
  @DisplayName("valid response with match")
  void validWithMatch() {
    var resp = new AnalyzeAudioExtractResponse("clean audio", true);
    assertEquals("clean audio", resp.analysis());
    assertTrue(resp.matchesCriteria());
  }

  @Test
  @DisplayName("valid response without match")
  void validWithoutMatch() {
    var resp = new AnalyzeAudioExtractResponse("flagged content", false);
    assertFalse(resp.matchesCriteria());
  }
}
