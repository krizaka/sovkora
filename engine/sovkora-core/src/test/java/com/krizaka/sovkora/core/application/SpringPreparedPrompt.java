package com.krizaka.sovkora.core.application;

import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/** Test double of the adapter's prompt: the core's tests cannot see the adapter module. */
public record SpringPreparedPrompt(Prompt prompt, String featureKey) implements PreparedPrompt {

  public SpringPreparedPrompt(Prompt prompt) {
    this(prompt, null);
  }

  @Override
  public PreparedPrompt forFeature(String key) {
    return new SpringPreparedPrompt(prompt, key);
  }

  @Override
  public int messageCount() {
    return prompt.getInstructions().size();
  }

  @Override
  public String modelName() {
    return prompt.getOptions() == null ? null : prompt.getOptions().getModel();
  }

  @Override
  public PreparedPrompt withoutTools() {
    return new SpringPreparedPrompt(
        new Prompt(
            prompt.getInstructions(),
            prompt.getOptions() == null
                ? null
                : ChatOptions.builder()
                    .model(prompt.getOptions().getModel())
                    .temperature(prompt.getOptions().getTemperature())
                    .maxTokens(prompt.getOptions().getMaxTokens())
                    .build()),
        featureKey);
  }
}
