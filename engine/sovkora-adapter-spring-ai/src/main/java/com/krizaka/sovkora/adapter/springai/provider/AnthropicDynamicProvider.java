package com.krizaka.sovkora.adapter.springai.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;

/** Dynamic ChatModel provider for Anthropic (Claude) models. */
public class AnthropicDynamicProvider implements DynamicChatModelProvider {

  private final ObservationRegistry observationRegistry;

  /** Without a host registry: nothing is observed. */
  public AnthropicDynamicProvider() {
    this(ObservationRegistry.NOOP);
  }

  /**
   * @param observationRegistry the host's registry, so Spring AI records {@code
   *     gen_ai.client.operation} for every model built here (ADR-094)
   */
  public AnthropicDynamicProvider(ObservationRegistry observationRegistry) {
    this.observationRegistry = Objects.requireNonNull(observationRegistry, "registry");
  }

  @Override
  public boolean supports(String providerName) {
    return "claude".equalsIgnoreCase(providerName) || "anthropic".equalsIgnoreCase(providerName);
  }

  @Override
  public ChatModel create(String modelName, String apiKey) {
    if (modelName == null || modelName.isBlank()) {
      throw new IllegalArgumentException("Model name is required for Anthropic dynamic provider");
    }
    AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    return AnthropicChatModel.builder()
        .anthropicClient(client)
        .options(
            AnthropicChatOptions.builder()
                .model(modelName)
                // No default cache option: the cache is decided per turn from the feature's
                // policy, and a SENSITIVE turn has none (SpringAiChatInvocation, #173).
                .build())
        .observationRegistry(observationRegistry)
        .build();
  }
}
