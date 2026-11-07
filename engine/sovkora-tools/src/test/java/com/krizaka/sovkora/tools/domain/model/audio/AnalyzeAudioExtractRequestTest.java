package com.krizaka.sovkora.tools.domain.model.audio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AnalyzeAudioExtractRequest} — compact constructor validation. */
class AnalyzeAudioExtractRequestTest {
  @org.junit.jupiter.api.Test
  void sonar_context_load() {
    assertNotNull(AnalyzeAudioExtractRequest.class);
  }

  @Nested
  @DisplayName("Constructor invariants")
  class Invariants {

    @Test
    @DisplayName("null clipPath throws NPE")
    void nullClipPath() {
      assertThrows(NullPointerException.class, () -> new AnalyzeAudioExtractRequest(null, null));
    }

    @Test
    @DisplayName("blank clipPath throws IAE")
    void blankClipPath() {
      assertThrows(
          IllegalArgumentException.class, () -> new AnalyzeAudioExtractRequest("  ", null));
    }

    @Test
    @DisplayName("null checkType defaults to compliance")
    void nullCheckTypeDefaults() {
      var req = new AnalyzeAudioExtractRequest("/path/clip.mp3", null);
      assertEquals("compliance", req.checkType());
    }

    @Test
    @DisplayName("blank checkType defaults to compliance")
    void blankCheckTypeDefaults() {
      var req = new AnalyzeAudioExtractRequest("/path/clip.mp3", "  ");
      assertEquals("compliance", req.checkType());
    }

    @Test
    @DisplayName("explicit checkType preserved")
    void explicitCheckType() {
      var req = new AnalyzeAudioExtractRequest("/path/clip.mp3", "profanity");
      assertEquals("profanity", req.checkType());
    }
  }
}
