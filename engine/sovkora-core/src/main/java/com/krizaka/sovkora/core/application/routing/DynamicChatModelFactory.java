package com.krizaka.sovkora.core.application.routing;

/**
 * Port that builds, per request, the chat model of a commercial provider from the user's own key.
 * The model type is the adapter's, so the core names no vendor SDK.
 *
 * @param <M> the chat model type the adapter produces
 */
public interface DynamicChatModelFactory<M> {

  /**
   * Instantiates a model for the given provider, model name and decrypted API key.
   *
   * @param providerName The provider name ("openai", "claude"/"anthropic", "gemini").
   * @param modelName The target model name.
   * @param apiKey The user's decrypted API key.
   * @return A request-scoped model.
   * @throws IllegalArgumentException if the model name is blank or the provider unsupported
   */
  M createChatModel(String providerName, String modelName, String apiKey);
}
