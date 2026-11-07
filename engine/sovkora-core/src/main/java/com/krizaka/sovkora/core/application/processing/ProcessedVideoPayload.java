package com.krizaka.sovkora.core.application.processing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Immutable payload carrying extracted keyframes and audio transcript from a processed video.
 *
 * <p>Compact constructor enforces defensive copying and null-safety per ADR-007.
 *
 * @param audioTranscript The transcribed audio track (empty string if unavailable).
 * @param keyframes Extracted keyframe images as raw byte arrays.
 * @param durationSeconds How long the source was, as the transcription provider reported it; the
 *     measurement video analysis bills on, {@code null} when none was reported (ADR-066).
 */
public record ProcessedVideoPayload(
    String audioTranscript, List<byte[]> keyframes, BigDecimal durationSeconds) {

  /** Compact constructor enforcing immutability and null-safety. */
  public ProcessedVideoPayload {
    audioTranscript = Objects.requireNonNullElse(audioTranscript, "");
    keyframes = keyframes == null ? List.of() : List.copyOf(keyframes);
    durationSeconds =
        durationSeconds != null && durationSeconds.compareTo(BigDecimal.ZERO) > 0
            ? durationSeconds
            : null;
  }

  /**
   * A processed video whose source duration is unknown.
   *
   * @param audioTranscript the transcribed audio track
   * @param keyframes the extracted keyframes
   */
  public ProcessedVideoPayload(String audioTranscript, List<byte[]> keyframes) {
    this(audioTranscript, keyframes, null);
  }
}
