package com.krizaka.sovkora.core.application.processing;

/**
 * Port interface for pre-processing audio input before cognitive analysis.
 *
 * <p>Implementations extract transcripts and metadata from raw audio bytes for downstream
 * multi-modal model consumption.
 */
public interface AudioPreProcessor {

  /**
   * Processes raw audio bytes into a structured payload containing the audio transcript.
   *
   * @param audioBytes The raw audio file content.
   * @return A structured payload ready for multi-modal model consumption.
   */
  ProcessedAudioPayload process(byte[] audioBytes);

  /**
   * Processes raw audio bytes using a dynamic model name into a structured payload containing the
   * audio transcript.
   *
   * @param audioBytes The raw audio file content.
   * @param model The dynamic model to use for analysis.
   * @return A structured payload ready for multi-modal model consumption.
   */
  default ProcessedAudioPayload process(byte[] audioBytes, String model) {
    return process(audioBytes);
  }
}
