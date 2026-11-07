package com.krizaka.sovkora.adapter.springai.prompt;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/** Ollama-specific options strategy implementation. */
public class OllamaOptionsStrategy implements PipelineOptionsStrategy {

  @Override
  public boolean supports(String provider) {
    return "ollama".equalsIgnoreCase(provider);
  }

  @Override
  public ChatOptions buildOptions(String modelName, Double temperature) {
    return OllamaChatOptions.builder()
        .model(modelName)
        .temperature(temperature)
        .numCtx(8192)
        .build();
  }
}
