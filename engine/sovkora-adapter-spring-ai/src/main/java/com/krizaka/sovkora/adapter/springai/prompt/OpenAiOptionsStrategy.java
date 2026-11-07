package com.krizaka.sovkora.adapter.springai.prompt;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

/** OpenAI-specific options strategy implementation. */
public class OpenAiOptionsStrategy implements PipelineOptionsStrategy {

  @Override
  public boolean supports(String provider) {
    return "openai".equalsIgnoreCase(provider);
  }

  @Override
  public ChatOptions buildOptions(String modelName, Double temperature) {
    return OpenAiChatOptions.builder().model(modelName).temperature(temperature).build();
  }
}
