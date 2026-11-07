package com.krizaka.sovkora.core.infrastructure.adapter.processor;

import static com.krizaka.sovkora.test.TestConstants.PROVIDER_OLLAMA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.application.processing.ProcessedAudioPayload;
import com.krizaka.sovkora.core.domain.ports.outbound.AudioTranscriber;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.core.infrastructure.support.PipelineExecutionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/** Unit tests for {@link LocalVideoProcessor} keyframe extraction and configuration resolution. */
class LocalVideoProcessorTest {

  private static CoreProperties propsWithAnalysis() {
    return new CoreProperties(
        PROVIDER_OLLAMA,
        null,
        null,
        null,
        new CoreProperties.VideoConfig(
            new CoreProperties.VideoAnalysisConfig(8, 5),
            new CoreProperties.VideoGenerationConfig(null, null)),
        new CoreProperties.ImageConfig(
            new CoreProperties.ImageGenerationConfig(
                null,
                "http://test-image-server:8086",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null)),
        new CoreProperties.VisionConfig(PROVIDER_OLLAMA, "llama3.1:8b"),
        new CoreProperties.AudioConfig(PROVIDER_OLLAMA, "llama3.1:8b", "whisper-1"));
  }

  private static CoreProperties propsWithNullVideo() {
    return new CoreProperties(
        PROVIDER_OLLAMA,
        null,
        null,
        null,
        null,
        new CoreProperties.ImageConfig(
            new CoreProperties.ImageGenerationConfig(
                null,
                "http://test-image-server:8086",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null)),
        new CoreProperties.VisionConfig(PROVIDER_OLLAMA, "llama3.1:8b"),
        new CoreProperties.AudioConfig(PROVIDER_OLLAMA, "llama3.1:8b", "whisper-1"));
  }

  @Nested
  @DisplayName("process()")
  class Process {

    @Test
    @DisplayName("extracts keyframes from video bytes")
    void extractsKeyframes() {
      var whisper = mock(AudioTranscriber.class);
      var processor = new LocalVideoProcessor(propsWithAnalysis(), null, whisper);
      var payload = processor.process(new byte[100]);

      assertNotNull(payload);
      assertFalse(payload.keyframes().isEmpty());
      assertTrue(payload.keyframes().size() <= 8);
    }

    @Test
    @DisplayName("returns empty transcript for all-zeros input")
    void returnsEmptyTranscript() {
      var whisper = mock(AudioTranscriber.class);
      var processor = new LocalVideoProcessor(propsWithAnalysis(), null, whisper);
      var payload = processor.process(new byte[50]);

      assertEquals("", payload.audioTranscript());
    }

    @Test
    @DisplayName("throws NullPointerException for null video bytes")
    void throwsForNullBytes() {
      var whisper = mock(AudioTranscriber.class);
      var processor = new LocalVideoProcessor(propsWithAnalysis(), null, whisper);
      assertThrows(NullPointerException.class, () -> processor.process(null));
    }

    @Test
    @DisplayName("handles small video input gracefully")
    void handlesSmallInput() {
      var whisper = mock(AudioTranscriber.class);
      var processor = new LocalVideoProcessor(propsWithAnalysis(), null, whisper);
      var payload = processor.process(new byte[3]);

      assertNotNull(payload);
      assertFalse(payload.keyframes().isEmpty());
    }

    @Test
    @DisplayName("respects maxKeyframes limit")
    void respectsMaxKeyframes() {
      var whisper = mock(AudioTranscriber.class);
      var processor = new LocalVideoProcessor(propsWithAnalysis(), null, whisper);
      var payload = processor.process(new byte[1000]);

      assertTrue(payload.keyframes().size() <= 8);
    }

    @Test
    @DisplayName("returns plain text transcript if input is text")
    void returnsPlainTextTranscript() {
      var whisper = mock(AudioTranscriber.class);
      var processor = new LocalVideoProcessor(propsWithAnalysis(), null, whisper);
      byte[] textBytes = "hello video content".getBytes(StandardCharsets.UTF_8);
      var payload = processor.process(textBytes);

      assertEquals("hello video content", payload.audioTranscript());
      verifyNoInteractions(whisper);
    }

    @Test
    @DisplayName("processes binary media through FFmpeg and Whisper client")
    void processesBinaryMedia() {
      var whisper = mock(AudioTranscriber.class);
      var endpointResolver = new ProviderEndpointResolver(null);
      when(whisper.transcribe(any(), any(), any(), any()))
          .thenReturn(new ProcessedAudioPayload("transcribed text"));

      var processor = new LocalVideoProcessor(propsWithAnalysis(), endpointResolver, whisper);
      // Binary data containing control characters below 32 (e.g. 1, 2, 0)
      byte[] binaryBytes = new byte[] {1, 2, 3, 0, 4, 5, 0, 1, 2, 3, 0, 4, 5};

      try (MockedConstruction<ProcessBuilder> mocked =
          mockConstruction(
              ProcessBuilder.class,
              (mock, context) -> {
                java.lang.Process mockProcess = mock(java.lang.Process.class);
                when(mockProcess.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
                when(mockProcess.exitValue()).thenReturn(0);

                when(mock.start())
                    .thenAnswer(
                        invocation -> {
                          String[] command = (String[]) context.arguments().getFirst();
                          String outputPath = command[command.length - 1];
                          Files.write(Path.of(outputPath), new byte[] {9, 8, 7});
                          return mockProcess;
                        });
              })) {
        var payload = processor.process(binaryBytes);
        assertEquals("transcribed text", payload.audioTranscript());
        verify(whisper, times(1)).transcribe(any(), any(), any(), any());
      }
    }

    @Test
    @DisplayName("throws PipelineExecutionException when audio extraction fails")
    void throwsOnExtractionFailure() {
      var whisper = mock(AudioTranscriber.class);
      var processor = new LocalVideoProcessor(propsWithAnalysis(), null, whisper);
      byte[] binaryBytes = new byte[] {1, 2, 3, 0, 4, 5, 0, 1, 2, 3, 0, 4, 5};

      try (MockedConstruction<ProcessBuilder> mocked =
          mockConstruction(
              ProcessBuilder.class,
              (mock, context) -> {
                when(mock.start()).thenThrow(new IOException("ffmpeg failed"));
              })) {
        assertThrows(PipelineExecutionException.class, () -> processor.process(binaryBytes));
      }
    }

    @Test
    @DisplayName("throws PipelineExecutionException when whisper client fails")
    void throwsOnWhisperFailure() {
      var whisper = mock(AudioTranscriber.class);
      var endpointResolver = new ProviderEndpointResolver(null);
      when(whisper.transcribe(any(), any(), any(), any()))
          .thenThrow(new RuntimeException("whisper error"));

      var processor = new LocalVideoProcessor(propsWithAnalysis(), endpointResolver, whisper);
      byte[] binaryBytes = new byte[] {1, 2, 3, 0, 4, 5, 0, 1, 2, 3, 0, 4, 5};

      try (MockedConstruction<ProcessBuilder> mocked =
          mockConstruction(
              ProcessBuilder.class,
              (mock, context) -> {
                java.lang.Process mockProcess = mock(java.lang.Process.class);
                when(mockProcess.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
                when(mockProcess.exitValue()).thenReturn(0);

                when(mock.start())
                    .thenAnswer(
                        invocation -> {
                          String[] command = (String[]) context.arguments().getFirst();
                          String outputPath = command[command.length - 1];
                          Files.write(Path.of(outputPath), new byte[] {9, 8, 7});
                          return mockProcess;
                        });
              })) {
        assertThrows(PipelineExecutionException.class, () -> processor.process(binaryBytes));
      }
    }
  }

  @Nested
  @DisplayName("Configuration resolution")
  class ConfigResolution {

    @Test
    @DisplayName("falls back to defaults when video config is null")
    void fallsBackWhenNull() {
      var whisper = mock(AudioTranscriber.class);
      var processor = new LocalVideoProcessor(propsWithNullVideo(), null, whisper);
      var payload = processor.process(new byte[100]);

      assertNotNull(payload);
      assertTrue(payload.keyframes().size() <= 8); // default maxKeyframes
    }

    @Test
    @DisplayName("throws NullPointerException when properties is null")
    void throwsWhenPropertiesNull() {
      var whisper = mock(AudioTranscriber.class);
      assertThrows(NullPointerException.class, () -> new LocalVideoProcessor(null, null, whisper));
    }
  }
}
