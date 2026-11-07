package com.krizaka.sovkora.adapter.springai.provider;

import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/** Dynamic ChatModel provider for OpenAI models (Spring AI 2.0 / official com.openai SDK). */
public class OpenAiDynamicProvider implements DynamicChatModelProvider {

  private final ObservationRegistry observationRegistry;

  /** Without a host registry: nothing is observed. */
  public OpenAiDynamicProvider() {
    this(ObservationRegistry.NOOP);
  }

  /**
   * @param observationRegistry the host's registry, so Spring AI records {@code
   *     gen_ai.client.operation} for every model built here (ADR-094)
   */
  public OpenAiDynamicProvider(ObservationRegistry observationRegistry) {
    this.observationRegistry = Objects.requireNonNull(observationRegistry, "registry");
  }

  @Override
  public boolean supports(String providerName) {
    return "openai".equalsIgnoreCase(providerName);
  }

  @Override
  public ChatModel create(String modelName, String apiKey) {
    if (modelName == null || modelName.isBlank()) {
      throw new IllegalArgumentException("Model name is required for OpenAI dynamic provider");
    }
    // Spring AI 2.0: OpenAiChatModel.build() derives its (sync + async) com.openai client from the
    // options via OpenAiSetup, so the credential must live on the options, not a hand-built client.
    return OpenAiChatModel.builder()
        .options(OpenAiChatOptions.builder().model(modelName).apiKey(apiKey).build())
        .observationRegistry(observationRegistry)
        .build();
  }
}
