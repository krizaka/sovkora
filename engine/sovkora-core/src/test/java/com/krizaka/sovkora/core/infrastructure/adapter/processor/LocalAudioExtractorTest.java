package com.krizaka.sovkora.core.infrastructure.adapter.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class LocalAudioExtractorTest {

  @Test
  @DisplayName("extractAudio returns empty array for null input")
  void returnsEmptyForNull() {
    var extractor = new LocalAudioExtractor();
    byte[] result = extractor.extractAudio(null);
    assertNotNull(result);
    assertEquals(0, result.length);
  }

  @Test
  @DisplayName("extractAudio returns empty array for empty input")
  void returnsEmptyForEmpty() {
    var extractor = new LocalAudioExtractor();
    byte[] result = extractor.extractAudio(new byte[0]);
    assertNotNull(result);
    assertEquals(0, result.length);
  }

  @Test
  @DisplayName("extractAudio returns transcribed bytes on successful FFmpeg run")
  void returnsBytesOnSuccess() {
    var extractor = new LocalAudioExtractor();
    byte[] videoBytes = new byte[] {1, 2, 3};

    try (MockedConstruction<ProcessBuilder> mocked =
        mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              Process mockProcess = mock(Process.class);
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
      byte[] result = extractor.extractAudio(videoBytes);
      assertThat(result).containsExactly(9, 8, 7);
      assertThat(mocked.constructed()).hasSize(1);
    }
  }

  @Test
  @DisplayName("extractAudio throws exception when FFmpeg execution times out")
  void throwsOnTimeout() {
    var extractor = new LocalAudioExtractor();
    byte[] videoBytes = new byte[] {1, 2, 3};

    try (MockedConstruction<ProcessBuilder> mocked =
        mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              Process mockProcess = mock(Process.class);
              when(mockProcess.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);

              when(mock.start()).thenReturn(mockProcess);
            })) {
      assertThrows(IllegalStateException.class, () -> extractor.extractAudio(videoBytes));
    }
  }

  @Test
  @DisplayName("extractAudio throws exception when FFmpeg exit code is non-zero")
  void throwsOnNonZeroExitCode() {
    var extractor = new LocalAudioExtractor();
    byte[] videoBytes = new byte[] {1, 2, 3};

    try (MockedConstruction<ProcessBuilder> mocked =
        mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              Process mockProcess = mock(Process.class);
              when(mockProcess.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
              when(mockProcess.exitValue()).thenReturn(1);

              when(mock.start())
                  .thenAnswer(
                      invocation -> {
                        // Write empty or error log file (which is the third to last command
                        // argument)
                        // pb args: ffmpeg -y -i <video> -vn -acodec libmp3lame -ar 16000 -ac 1
                        // <audio>
                        // But pb also redirects output to tempLogFile. Let's just return process.
                        return mockProcess;
                      });
            })) {
      assertThrows(IllegalStateException.class, () -> extractor.extractAudio(videoBytes));
    }
  }

  @Test
  @DisplayName("extractAudio throws exception when start throws IOException")
  void throwsOnStartException() {
    var extractor = new LocalAudioExtractor();
    byte[] videoBytes = new byte[] {1, 2, 3};

    try (MockedConstruction<ProcessBuilder> mocked =
        mockConstruction(
            ProcessBuilder.class,
            (mock, context) -> {
              when(mock.start()).thenThrow(new IOException("ffmpeg executable not found"));
            })) {
      assertThrows(IllegalStateException.class, () -> extractor.extractAudio(videoBytes));
    }
  }
}
