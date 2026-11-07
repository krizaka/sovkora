package com.krizaka.sovkora.tools.domain.model.poster;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AnalyzePosterRequest} — compact constructor validation. */
class AnalyzePosterRequestTest {
  @org.junit.jupiter.api.Test
  void sonar_context_load() {
    assertNotNull(AnalyzePosterRequest.class);
  }

  @Nested
  @DisplayName("Constructor invariants")
  class Invariants {

    @Test
    @DisplayName("null posterBase64 throws NPE")
    void nullPoster() {
      assertThrows(NullPointerException.class, () -> new AnalyzePosterRequest(null, null));
    }

    @Test
    @DisplayName("blank posterBase64 throws IAE")
    void blankPoster() {
      assertThrows(IllegalArgumentException.class, () -> new AnalyzePosterRequest("  ", null));
    }

    @Test
    @DisplayName("null prompt defaults to analysis instruction")
    void nullPromptDefaults() {
      var req = new AnalyzePosterRequest("base64data", null);
      assertTrue(req.prompt().contains("Analyze this movie poster"));
    }

    @Test
    @DisplayName("blank prompt defaults to analysis instruction")
    void blankPromptDefaults() {
      var req = new AnalyzePosterRequest("base64data", "  ");
      assertTrue(req.prompt().contains("Analyze this movie poster"));
    }

    @Test
    @DisplayName("explicit prompt preserved")
    void explicitPrompt() {
      var req = new AnalyzePosterRequest("base64data", "Describe the colors");
      assertEquals("Describe the colors", req.prompt());
    }
  }
}
