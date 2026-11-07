package com.krizaka.sovkora.core.application.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VideoPreProcessorTest {

  @Test
  @DisplayName("default process method delegates to primary process method")
  void defaultProcessDelegates() {
    VideoPreProcessor preProcessor =
        new VideoPreProcessor() {
          @Override
          public ProcessedVideoPayload process(byte[] videoBytes) {
            return new ProcessedVideoPayload("transcript1", List.of(new byte[] {1}));
          }
        };

    byte[] testBytes = new byte[] {1, 2, 3};
    ProcessedVideoPayload payload = preProcessor.process(testBytes, "some-model");

    assertNotNull(payload);
    assertEquals("transcript1", payload.audioTranscript());
    assertEquals(1, payload.keyframes().size());
  }
}
