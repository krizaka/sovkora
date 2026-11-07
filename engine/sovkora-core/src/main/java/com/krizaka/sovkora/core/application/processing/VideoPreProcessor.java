package com.krizaka.sovkora.core.application.processing;

/**
 * Port interface for pre-processing video input before cognitive analysis.
 *
 * <p>Implementations extract keyframes and transcripts from raw video bytes for downstream
 * multi-modal model consumption. Activated conditionally via {@code
 * orazaka.engine.video.analysis.enabled=true}.
 */
public interface VideoPreProcessor {

  /**
   * Processes raw video bytes into a structured payload containing extracted keyframes and an audio
   * transcript.
   *
   * @param videoBytes The raw video file content.
   * @return A structured payload ready for multi-modal model consumption.
   */
  ProcessedVideoPayload process(byte[] videoBytes);

  /**
   * Processes raw video bytes using a dynamic model name into a structured payload containing
   * extracted keyframes and an audio transcript.
   *
   * @param videoBytes The raw video file content.
   * @param model The dynamic model to use for analysis.
   * @return A structured payload ready for multi-modal model consumption.
   */
  default ProcessedVideoPayload process(byte[] videoBytes, String model) {
    return process(videoBytes);
  }
}
