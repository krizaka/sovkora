package com.krizaka.sovkora.tools.domain.model.poster;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AnalyzePosterResponse} — compact constructor validation. */
class AnalyzePosterResponseTest {

  @Test
  @DisplayName("null analysis throws NPE")
  void nullAnalysis() {
    assertThrows(NullPointerException.class, () -> new AnalyzePosterResponse(null, true));
  }

  @Test
  @DisplayName("valid response preserved")
  void validResponse() {
    var resp = new AnalyzePosterResponse("dark tone detected", true);
    assertEquals("dark tone detected", resp.analysis());
    assertTrue(resp.success());
  }

  @Test
  @DisplayName("failure response preserved")
  void failureResponse() {
    var resp = new AnalyzePosterResponse("error occurred", false);
    assertFalse(resp.success());
  }
}
