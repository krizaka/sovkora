package com.krizaka.sovkora.tools.domain.model.audio;

import java.util.Objects;

/** Response returned from film audio extract analysis. */
public record AnalyzeAudioExtractResponse(String analysis, boolean matchesCriteria) {
  public AnalyzeAudioExtractResponse {
    Objects.requireNonNull(analysis, "analysis must not be null");
  }
}
