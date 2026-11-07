package com.krizaka.sovkora.core.infrastructure.adapter.processor;

import static com.krizaka.sovkora.test.TestConstants.PROVIDER_OLLAMA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.application.processing.ProcessedAudioPayload;
import com.krizaka.sovkora.core.domain.ports.outbound.AudioTranscriber;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalAudioProcessorTest {

  private static CoreProperties propsWithAudio() {
    return new CoreProperties(
        PROVIDER_OLLAMA,
        null,
        null,
        null,
        null,
        null,
        null,
        new CoreProperties.AudioConfig(PROVIDER_OLLAMA, "llama3.1:8b", "whisper-1"));
  }

  private static CoreProperties propsWithNullAudio() {
    return new CoreProperties(PROVIDER_OLLAMA, null, null, null, null, null, null, null);
  }

  @Test
  @DisplayName("Constructor throws NullPointerException when properties is null")
  void constructorThrowsWhenPropertiesNull() {
    var endpointResolver = new ProviderEndpointResolver(null);
    var whisper = mock(AudioTranscriber.class);
    assertThrows(
        NullPointerException.class, () -> new LocalAudioProcessor(null, endpointResolver, whisper));
  }

  @Test
  @DisplayName("Constructor throws NullPointerException when whisperClient is null")
  void constructorThrowsWhenWhisperNull() {
    var endpointResolver = new ProviderEndpointResolver(null);
    var props = propsWithAudio();
    assertThrows(
        NullPointerException.class, () -> new LocalAudioProcessor(props, endpointResolver, null));
  }

  @Test
  @DisplayName("process throws NullPointerException when audioBytes is null")
  void processThrowsWhenBytesNull() {
    var endpointResolver = new ProviderEndpointResolver(null);
    var whisper = mock(AudioTranscriber.class);
    var processor = new LocalAudioProcessor(propsWithAudio(), endpointResolver, whisper);

    assertThrows(NullPointerException.class, () -> processor.process(null));
  }

  @Test
  @DisplayName("process returns empty payload when audioBytes is empty")
  void processReturnsEmptyWhenBytesEmpty() {
    var endpointResolver = new ProviderEndpointResolver(null);
    var whisper = mock(AudioTranscriber.class);
    var processor = new LocalAudioProcessor(propsWithAudio(), endpointResolver, whisper);

    ProcessedAudioPayload payload = processor.process(new byte[0]);
    assertNotNull(payload);
    assertEquals("", payload.transcript());
    verifyNoInteractions(whisper);
  }

  @Test
  @DisplayName("process returns empty payload when audioBytes is all zeros")
  void processReturnsEmptyWhenBytesAllZeros() {
    var endpointResolver = new ProviderEndpointResolver(null);
    var whisper = mock(AudioTranscriber.class);
    var processor = new LocalAudioProcessor(propsWithAudio(), endpointResolver, whisper);

    ProcessedAudioPayload payload = processor.process(new byte[100]);
    assertNotNull(payload);
    assertEquals("", payload.transcript());
    verifyNoInteractions(whisper);
  }

  @Test
  @DisplayName("process returns transcribed text on success")
  void processReturnsTranscriptOnSuccess() {
    var endpointResolver = new ProviderEndpointResolver(null);
    var whisper = mock(AudioTranscriber.class);
    when(whisper.transcribe(any(), any(), any(), any()))
        .thenReturn(new ProcessedAudioPayload("mocked transcription"));

    var processor = new LocalAudioProcessor(propsWithAudio(), endpointResolver, whisper);
    // Non-zero bytes to bypass all-zeros check
    byte[] audioBytes = new byte[] {1, 2, 3};

    ProcessedAudioPayload payload = processor.process(audioBytes);
    assertNotNull(payload);
    assertEquals("mocked transcription", payload.transcript());
    verify(whisper, times(1)).transcribe(any(), any(), any(), any());
  }

  @Test
  @DisplayName(
      "process handles default audio model fallback when configuration lacks transcription model")
  void processUsesFallbackModel() {
    var endpointResolver = new ProviderEndpointResolver(null);
    var whisper = mock(AudioTranscriber.class);
    when(whisper.transcribe(any(), any(), any(), any()))
        .thenReturn(new ProcessedAudioPayload("fallback text"));

    var processor = new LocalAudioProcessor(propsWithNullAudio(), endpointResolver, whisper);
    byte[] audioBytes = new byte[] {1, 2, 3};

    ProcessedAudioPayload payload = processor.process(audioBytes);
    assertNotNull(payload);
    assertEquals("fallback text", payload.transcript());
    verify(whisper, times(1)).transcribe(any(), any(), eq("speech.mp3"), eq("whisper-1"));
  }

  @Test
  @DisplayName(
      "process throws UncheckedIOException when Whisper throws exception with IOException cause")
  void processThrowsUncheckedIOException() {
    var endpointResolver = new ProviderEndpointResolver(null);
    var whisper = mock(AudioTranscriber.class);
    IOException ioCause = new IOException("socket closed");
    when(whisper.transcribe(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("failed", ioCause));

    var processor = new LocalAudioProcessor(propsWithAudio(), endpointResolver, whisper);
    byte[] audioBytes = new byte[] {1, 2, 3};

    assertThrows(UncheckedIOException.class, () -> processor.process(audioBytes));
  }

  @Test
  @DisplayName("process propagates original RuntimeException when cause is not IOException")
  void processPropagatesOriginalException() {
    var endpointResolver = new ProviderEndpointResolver(null);
    var whisper = mock(AudioTranscriber.class);
    when(whisper.transcribe(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("custom failure"));

    var processor = new LocalAudioProcessor(propsWithAudio(), endpointResolver, whisper);
    byte[] audioBytes = new byte[] {1, 2, 3};

    var ex = assertThrows(RuntimeException.class, () -> processor.process(audioBytes));
    assertEquals("custom failure", ex.getMessage());
  }
}
