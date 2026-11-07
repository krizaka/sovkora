package com.krizaka.sovkora.core.infrastructure.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.application.engine.Engine;
import com.krizaka.sovkora.core.domain.model.audio.AudioRequest;
import com.krizaka.sovkora.core.domain.model.audio.AudioResponse;
import org.junit.jupiter.api.Test;

class DefaultAudioGeneratorClientTest {

  @Test
  void constructorNullCheck() {
    assertThatThrownBy(() -> new DefaultAudioGeneratorClient(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Engine must not be null");
  }

  @Test
  void generateAudio() {
    Engine engine = mock(Engine.class);
    DefaultAudioGeneratorClient client = new DefaultAudioGeneratorClient(engine);

    AudioRequest request =
        new AudioRequest(
            "hello world",
            "alloy",
            "tts-1",
            java.util.Map.of(),
            com.krizaka.sovkora.core.domain.model.Context.anonymous());
    byte[] mockAudio = new byte[] {1, 2, 3};
    when(engine.generateSpeech(request)).thenReturn(mockAudio);

    AudioResponse response = client.generateAudio(request);
    assertThat(response).isNotNull();
    assertThat(response.audioData()).isEqualTo(mockAudio);
    assertThat(response.format()).isEqualTo("mp3");
  }

  @Test
  void theDeclaredFormatIsTheFormatReturned() {
    Engine engine = mock(Engine.class);
    AudioRequest request =
        new AudioRequest(
            "bonjour",
            null,
            "piper-fr-siwis-medium",
            java.util.Map.of(AudioRequest.RESPONSE_FORMAT, "wav"),
            com.krizaka.sovkora.core.domain.model.Context.anonymous());
    when(engine.generateSpeech(request)).thenReturn(new byte[] {1});

    assertThat(new DefaultAudioGeneratorClient(engine).generateAudio(request).format())
        .isEqualTo("wav");
  }
}
