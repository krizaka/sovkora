package com.krizaka.sovkora.adapter.springai.provider;

import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

/** Dynamic ChatModel provider for Google Gemini models. */
public class GeminiDynamicProvider implements DynamicChatModelProvider {

  private final ObservationRegistry observationRegistry;

  /** Without a host registry: nothing is observed. */
  public GeminiDynamicProvider() {
    this(ObservationRegistry.NOOP);
  }

  /**
   * @param observationRegistry the host's registry, so Spring AI records {@code
   *     gen_ai.client.operation} for every model built here (ADR-094)
   */
  public GeminiDynamicProvider(ObservationRegistry observationRegistry) {
    this.observationRegistry = Objects.requireNonNull(observationRegistry, "registry");
  }

  @Override
  public boolean supports(String providerName) {
    return "gemini".equalsIgnoreCase(providerName) || "google".equalsIgnoreCase(providerName);
  }

  @Override
  public ChatModel create(String modelName, String apiKey) {
    if (modelName == null || modelName.isBlank()) {
      throw new IllegalArgumentException("Model name is required for Gemini dynamic provider");
    }
    Client client = Client.builder().apiKey(apiKey).build();
    GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().model(modelName).build();
    return GoogleGenAiChatModel.builder()
        .genAiClient(client)
        .options(options)
        .observationRegistry(observationRegistry)
        .build();
  }
}
