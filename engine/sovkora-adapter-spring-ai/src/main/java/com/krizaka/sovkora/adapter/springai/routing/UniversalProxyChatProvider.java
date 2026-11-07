package com.krizaka.sovkora.adapter.springai.routing;

import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Universal Proxy Client (ADR-030).
 *
 * <p>Builds an <b>OpenAI-compatible</b> {@link ChatModel} pointed at <b>any</b> provider's
 * base-url. Every local provider exposes an OpenAI-compatible endpoint — Ollama (via {@code /v1}),
 * LocalAI, and the commercial OpenAI API alike — so a single client reaches them all, configured
 * purely at runtime from the DB ({@code ai_providers} base-url/api-key + {@code orazaka_models}
 * model name).
 *
 * <p>Spring AI 2.0 builds {@code OpenAiChatModel} on the official {@code com.openai} Java SDK and
 * derives that client from the {@code OpenAiChatOptions} via {@code OpenAiSetup} — so the provider
 * endpoint (base-url + api-key) is set on the options. No Spring Boot starter or autoconfiguration
 * is involved — the {@link DynamicModelRouter} calls this per request with values resolved from the
 * catalog.
 *
 * @see DynamicModelRouter
 */
public class UniversalProxyChatProvider {

  private final ObservationRegistry observationRegistry;

  /** Without a host registry: nothing is observed. */
  public UniversalProxyChatProvider() {
    this(ObservationRegistry.NOOP);
  }

  /**
   * @param observationRegistry the host's registry, so Spring AI records {@code
   *     gen_ai.client.operation} for every model built here (ADR-094)
   */
  public UniversalProxyChatProvider(ObservationRegistry observationRegistry) {
    this.observationRegistry = Objects.requireNonNull(observationRegistry, "registry");
  }

  /**
   * Builds an OpenAI-compatible {@link ChatModel} for the given endpoint.
   *
   * @param baseUrl provider base-url from {@code ai_providers} (e.g. {@code
   *     http://localhost:11434/v1} for Ollama, {@code http://localhost:8085} for LocalAI)
   * @param apiKey provider api-key from {@code ai_providers} ({@code "not-required"} for local
   *     providers; a real key for commercial ones)
   * @param modelName the model name from {@code orazaka_models}
   * @return a ready-to-use {@link ChatModel} bound to that provider + model
   */
  public ChatModel chatModel(String baseUrl, String apiKey, String modelName) {
    // Spring AI 2.0: OpenAiChatModel.build() derives its com.openai client from the options via
    // OpenAiSetup, so base-url + api-key (the provider endpoint) live on the options.
    return OpenAiChatModel.builder()
        .options(
            OpenAiChatOptions.builder()
                .model(modelName)
                .apiKey(apiKey)
                .baseUrl(withApiVersion(baseUrl))
                .build())
        .observationRegistry(observationRegistry)
        .build();
  }

  /**
   * Ensures the base-url carries the {@code /v1} API version. The {@code com.openai} SDK appends
   * {@code /chat/completions} to the base-url <b>without</b> inserting the version, so every
   * OpenAI-compatible endpoint must already end in {@code /v1} (Ollama at {@code :11434/v1},
   * LocalAI, and the commercial API alike). {@code ai_providers} stores the host <i>root</i> —
   * deliberately, because the native Ollama management API (model catalog/pull) lives at the root,
   * not under {@code /v1} — so the version is appended here, only for the chat proxy, and only when
   * absent (idempotent for providers already configured with {@code /v1}).
   */
  static String withApiVersion(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return baseUrl;
    }
    String trimmed = baseUrl.replaceAll("/+$", "");
    return trimmed.endsWith("/v1") || trimmed.contains("/v1/") ? trimmed : trimmed + "/v1";
  }
}
