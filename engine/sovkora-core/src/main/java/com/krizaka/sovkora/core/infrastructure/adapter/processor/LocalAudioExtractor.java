package com.krizaka.sovkora.core.infrastructure.adapter.processor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to extract audio stream from raw video bytes locally using FFmpeg.
 *
 * <p>Avoids loading full video files into JVM heap and sending large payloads over the network to
 * transcription endpoints.
 */
public class LocalAudioExtractor {

  private static final Logger logger = LoggerFactory.getLogger(LocalAudioExtractor.class);
  private static final int EXTRACT_TIMEOUT_SEC = 30;

  /**
   * Extracts the audio from raw video bytes using FFmpeg command line.
   *
   * @param videoBytes The raw video file bytes.
   * @return A lightweight MP3 audio byte array.
   */
  @SuppressWarnings({"java:S5443", "java:S4036"})
  public byte[] extractAudio(byte[] videoBytes) {
    if (videoBytes == null || videoBytes.length == 0) {
      return new byte[0];
    }

    Path tempDir = null;
    File tempVideoFile = null;
    File tempAudioFile = null;
    File tempLogFile = null;
    try {
      tempDir =
          Files.createTempDirectory(
              "orazaka_ffmpeg_",
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      tempVideoFile = Files.createTempFile(tempDir, "video_input_", ".mp4").toFile();
      tempAudioFile = Files.createTempFile(tempDir, "audio_output_", ".mp3").toFile();
      tempLogFile = Files.createTempFile(tempDir, "ffmpeg_log_", ".log").toFile();

      Files.write(tempVideoFile.toPath(), videoBytes);

      // Build ffmpeg process
      // -vn: disable video, -acodec libmp3lame: MP3 encoder, -ar 16000: 16kHz sample rate, -ac 1:
      // mono channel
      ProcessBuilder pb =
          new ProcessBuilder(
              "ffmpeg",
              "-y",
              "-i",
              tempVideoFile.getAbsolutePath(),
              "-vn",
              "-acodec",
              "libmp3lame",
              "-ar",
              "16000",
              "-ac",
              "1",
              tempAudioFile.getAbsolutePath());

      pb.redirectErrorStream(true);
      pb.redirectOutput(tempLogFile);
      Process process = pb.start();

      boolean completed = process.waitFor(EXTRACT_TIMEOUT_SEC, TimeUnit.SECONDS);
      if (!completed) {
        process.destroyForcibly();
        throw new IllegalStateException(
            "FFmpeg audio extraction timed out after " + EXTRACT_TIMEOUT_SEC + " seconds");
      }

      int exitCode = process.exitValue();
      if (exitCode != 0) {
        String logContent = readLogContent(tempLogFile);
        throw new IllegalStateException(
            "FFmpeg audio extraction failed with exit code: " + exitCode + ". Log:\n" + logContent);
      }

      return Files.readAllBytes(tempAudioFile.toPath());
    } catch (IOException | InterruptedException e) {
      logger.error("FFmpeg audio extraction execution failed", e);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Audio extraction failure: " + e.getMessage(), e);
    } finally {
      // Zero disk leaks cleanup
      cleanupFile(tempVideoFile);
      cleanupFile(tempAudioFile);
      cleanupFile(tempLogFile);
      if (tempDir != null) {
        cleanupFile(tempDir.toFile());
      }
    }
  }

  private void cleanupFile(File file) {
    if (file != null && file.exists()) {
      try {
        Files.delete(file.toPath());
      } catch (IOException e) {
        logger.warn("Failed to delete temporary media file: {}", file.getAbsolutePath(), e);
      } catch (SecurityException e) {
        logger.warn(
            "Security exception deleting temporary media file: {}", file.getAbsolutePath(), e);
      }
    }
  }

  private String readLogContent(File logFile) {
    try {
      return Files.readString(logFile.toPath());
    } catch (IOException ex) {
      logger.warn("Failed to read FFmpeg log file: {}", logFile.getAbsolutePath(), ex);
      return "";
    }
  }
}
