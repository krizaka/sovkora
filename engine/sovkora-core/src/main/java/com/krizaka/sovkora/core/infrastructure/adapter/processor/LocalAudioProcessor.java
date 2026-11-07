package com.krizaka.sovkora.core.infrastructure.adapter.processor;

import com.krizaka.sovkora.core.application.processing.AudioPreProcessor;
import com.krizaka.sovkora.core.application.processing.ProcessedAudioPayload;
import com.krizaka.sovkora.core.domain.ports.outbound.AudioTranscriber;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Baseline audio processing infrastructure component. Handles local audio mutations, codec
 * conversions, or speech-to-text execution structures.
 */
public class LocalAudioProcessor implements AudioPreProcessor {

  private static final Logger logger = LoggerFactory.getLogger(LocalAudioProcessor.class);

  private final ProviderEndpointResolver providerEndpointResolver;
  private final AudioTranscriber whisperClient;
  private final String audioModel;

  public LocalAudioProcessor(
      CoreProperties properties,
      ProviderEndpointResolver providerEndpointResolver,
      AudioTranscriber whisperClient) {
    Objects.requireNonNull(properties, "CoreProperties must not be null");
    this.providerEndpointResolver = providerEndpointResolver;
    this.whisperClient = Objects.requireNonNull(whisperClient, "AudioTranscriber must not be null");
    String audioM = null;
    if (properties.audio() != null) {
      audioM = properties.audio().transcriptionModel();
    }
    if (audioM == null || audioM.isBlank()) {
      audioM = "whisper-1";
    }
    this.audioModel = audioM;
    logger.info("LocalAudioProcessor initialized: audioModel={}", this.audioModel);
  }

  @Override
  public ProcessedAudioPayload process(byte[] audioBytes) {
    return process(audioBytes, null);
  }

  @Override
  public ProcessedAudioPayload process(byte[] audioBytes, String model) {
    Objects.requireNonNull(audioBytes, "Audio bytes must not be null");
    logger.debug("Processing audio payload of size: {} bytes", audioBytes.length);
    if (audioBytes.length == 0) {
      return new ProcessedAudioPayload("");
    }

    // 1. If it's all zeros, return empty
    if (LocalProcessorUtil.isAllZeros(audioBytes)) {
      return new ProcessedAudioPayload("");
    }

    // Resolve base URL dynamically
    String resolvedBaseUrl = providerEndpointResolver.localAiBaseUrl();

    // 2. Perform Whisper transcription call via Spring RestClient
    String finalModel = LocalProcessorUtil.resolveWhisperModel(model, this.audioModel);
    logger.info(
        "LocalAudioProcessor: Transcribing audio binary content ({} bytes) via Whisper at {}...",
        audioBytes.length,
        resolvedBaseUrl);
    try {
      // The client's payload travels whole: its duration is what transcription bills on, and
      // rebuilding it here from the transcript alone is exactly how that measurement was lost.
      return whisperClient.transcribe(resolvedBaseUrl, audioBytes, "speech.mp3", finalModel);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof IOException ioEx) {
        throw new UncheckedIOException(
            "Whisper native transcription extraction failed: " + ioEx.getMessage(), ioEx);
      }
      throw e;
    }
  }
}
