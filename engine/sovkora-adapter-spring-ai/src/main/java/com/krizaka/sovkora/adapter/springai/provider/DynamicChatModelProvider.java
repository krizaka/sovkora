package com.krizaka.sovkora.adapter.springai.provider;

import org.springframework.ai.chat.model.ChatModel;

/** Strategy interface for dynamically constructing vendor-specific ChatModels. */
public interface DynamicChatModelProvider {

  /**
   * Returns whether this provider supports the given provider name.
   *
   * @param providerName The name of the AI provider.
   * @return true if supported, false otherwise.
   */
  boolean supports(String providerName);

  /**
   * Dynamically constructs the ChatModel instance.
   *
   * @param modelName The target model name.
   * @param apiKey The decrypted API key.
   * @return The ChatModel.
   */
  ChatModel create(String modelName, String apiKey);
}
