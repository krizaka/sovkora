package com.krizaka.sovkora.core.application.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AudioPreProcessorTest {

  @Test
  @DisplayName("default process method delegates to primary process method")
  void defaultProcessDelegates() {
    AudioPreProcessor preProcessor =
        new AudioPreProcessor() {
          @Override
          public ProcessedAudioPayload process(byte[] audioBytes) {
            return new ProcessedAudioPayload(
                "processed_with_len_" + (audioBytes != null ? audioBytes.length : 0));
          }
        };

    byte[] testBytes = new byte[] {1, 2, 3};
    ProcessedAudioPayload payload = preProcessor.process(testBytes, "some-model");

    assertNotNull(payload);
    assertEquals("processed_with_len_3", payload.transcript());
  }
}
