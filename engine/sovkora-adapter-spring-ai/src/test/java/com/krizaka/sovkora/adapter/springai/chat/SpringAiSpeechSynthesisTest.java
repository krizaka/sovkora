package com.krizaka.sovkora.adapter.springai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;

class SpringAiSpeechSynthesisTest {

  private final TextToSpeechModel model = mock(TextToSpeechModel.class);

  private void answering(byte[] audio) {
    var response = mock(TextToSpeechResponse.class);
    var speech = mock(Speech.class);
    when(speech.getOutput()).thenReturn(audio);
    when(response.getResult()).thenReturn(speech);
    when(model.call(any(TextToSpeechPrompt.class))).thenReturn(response);
  }

  @Test
  void speaksTheTextWithTheModelAndTheFormat() {
    answering(new byte[] {1, 2});
    var port = new SpringAiSpeechSynthesis(model);

    assertThat(port.synthesize("hello", "tts-1", "wav")).containsExactly(1, 2);

    var prompt = ArgumentCaptor.forClass(TextToSpeechPrompt.class);
    verify(model).call(prompt.capture());
    assertThat(prompt.getValue().getInstructions().getText()).isEqualTo("hello");
    var options = (OpenAiAudioSpeechOptions) prompt.getValue().getOptions();
    assertThat(options.getModel()).isEqualTo("tts-1");
    assertThat(options.getResponseFormat()).isNotNull();
  }

  @Test
  void leavesTheFormatToTheModelWhenNoneIsDeclared() {
    answering(new byte[] {3});

    assertThat(new SpringAiSpeechSynthesis(model).synthesize("hi", "tts-1", null))
        .containsExactly(3);
  }
}
