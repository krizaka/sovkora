package com.krizaka.sovkora.adapter.springai.prompt;

import org.springframework.ai.chat.prompt.ChatOptions;

/** Strategy interface for provider-specific ChatOptions building. */
public interface PipelineOptionsStrategy {

  /**
   * Determines if this strategy supports the given provider.
   *
   * @param provider The provider key.
   * @return true if supported, false otherwise.
   */
  boolean supports(String provider);

  /**
   * Builds provider-specific ChatOptions.
   *
   * @param modelName The model name.
   * @param temperature The temperature.
   * @return Configured ChatOptions.
   */
  ChatOptions buildOptions(String modelName, Double temperature);
}
