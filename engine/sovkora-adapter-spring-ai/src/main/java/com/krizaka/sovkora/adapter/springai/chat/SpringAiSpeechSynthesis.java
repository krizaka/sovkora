package com.krizaka.sovkora.adapter.springai.chat;

import com.krizaka.sovkora.core.domain.ports.outbound.SpeechSynthesis;
import java.util.Objects;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;

/** {@link SpeechSynthesis} on Spring AI's {@link TextToSpeechModel}. */
public final class SpringAiSpeechSynthesis implements SpeechSynthesis {

  private final TextToSpeechModel model;

  /**
   * Creates the port over a text-to-speech model.
   *
   * @param model the model (in the host, the routed one)
   */
  public SpringAiSpeechSynthesis(TextToSpeechModel model) {
    this.model = Objects.requireNonNull(model, "model");
  }

  @Override
  public byte[] synthesize(String text, String modelName, String responseFormat) {
    OpenAiAudioSpeechOptions.Builder options = OpenAiAudioSpeechOptions.builder().model(modelName);
    if (responseFormat != null) {
      options.responseFormat(responseFormat);
    }
    return model.call(new TextToSpeechPrompt(text, options.build())).getResult().getOutput();
  }
}
