package com.krizaka.sovkora.core.domain.model.audio;

import java.util.Arrays;
import java.util.Objects;

/**
 * Standardized audio response record.
 *
 * @param audioData The raw binary speech audio bytes.
 * @param format The target audio file format (defaults to "mp3").
 */
public record AudioResponse(byte[] audioData, String format) {

  public AudioResponse {
    Objects.requireNonNull(audioData, "audioData must not be null");
    if (audioData.length == 0) {
      throw new IllegalArgumentException("audioData must not be empty");
    }
    format = format != null && !format.isBlank() ? format : "mp3";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AudioResponse(byte[] otherData, String otherFormat))) {
      return false;
    }
    return Arrays.equals(audioData, otherData) && Objects.equals(format, otherFormat);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(audioData) + Objects.hashCode(format);
  }

  @Override
  public String toString() {
    return "AudioResponse[audioData=" + audioData.length + " bytes, format=" + format + "]";
  }
}
