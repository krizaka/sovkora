package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.application.processing.ProcessedAudioPayload;

/**
 * Outbound port: turns audio into a transcript through a Whisper-compatible service
 * (sovkora-engine#121: the HTTP client lives in the Spring adapter, the core only asks).
 */
public interface AudioTranscriber {

  /**
   * Transcribes one audio file.
   *
   * @param baseUrl the base URL of the transcription service
   * @param audioBytes the raw audio
   * @param filename the virtual filename of the upload (must be an audio file)
   * @param model the Whisper model identifier
   * @return the transcript and, when the provider reports one, the duration of the audio
   * @throws IllegalArgumentException if the file is not audio
   * @throws IllegalStateException if the response is missing or unparseable
   */
  ProcessedAudioPayload transcribe(
      String baseUrl, byte[] audioBytes, String filename, String model);
}
