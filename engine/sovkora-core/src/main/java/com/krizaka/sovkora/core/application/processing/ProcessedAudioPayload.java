package com.krizaka.sovkora.core.application.processing;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable payload carrying the transcribed text from a processed audio source, and how long that
 * source was.
 *
 * <p>The duration is the <b>measurement transcription bills on</b>: AUDIO_MINUTE prices a minute of
 * source audio, and until this component existed the executor had nothing to report, so every
 * transcription released its hold and was served free (ADR-066, audit #22). It is {@code null}
 * whenever the provider did not report one — an unmeasured job is released rather than billed at a
 * guess, which is the direction the error must run.
 *
 * <p>Compact constructor enforces null-safety per ADR-007.
 *
 * @param transcript The transcribed audio track (empty string if unavailable).
 * @param durationSeconds How long the source audio was, as the transcription provider reported it;
 *     {@code null} when it reported none.
 */
public record ProcessedAudioPayload(String transcript, BigDecimal durationSeconds) {

  /** Compact constructor enforcing null-safety. */
  public ProcessedAudioPayload {
    transcript = Objects.requireNonNullElse(transcript, "");
    durationSeconds =
        durationSeconds != null && durationSeconds.compareTo(BigDecimal.ZERO) > 0
            ? durationSeconds
            : null;
  }

  /**
   * A transcript whose source duration is unknown.
   *
   * @param transcript the transcribed text
   */
  public ProcessedAudioPayload(String transcript) {
    this(transcript, null);
  }
}
