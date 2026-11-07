package com.krizaka.sovkora.adapter.springai.prompt;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Resolves provider-specific {@link ChatOptions} from incoming request options or defaults.
 *
 * <p>Extracted from the pipeline bridge to isolate the options mapping concern. Handles
 * cross-provider mapping (e.g., OpenAI options → Ollama options when provider is Ollama) and
 * default fallback creation.
 */
public final class ChatOptionsResolver {

  private static final double DEFAULT_TEMPERATURE = 0.7;
  private static final int OLLAMA_DEFAULT_CTX = 8192;

  private ChatOptionsResolver() {}

  /**
   * Resolves Ollama-specific options, mapping from foreign option types if necessary.
   *
   * @param springOptions The original options (may be OpenAI, Ollama, or null).
   * @param ollamaChatModel The active Ollama chat model name.
   * @return Resolved {@link OllamaChatOptions}.
   */
  public static ChatOptions resolveOllamaOptions(
      ChatOptions springOptions, String ollamaChatModel) {
    return switch (springOptions) {
      case OpenAiChatOptions openaiOpt -> {
        String modelName = openaiOpt.getModel();
        if (modelName == null || modelName.toLowerCase().contains("gpt")) {
          modelName = ollamaChatModel;
        }
        yield OllamaChatOptions.builder()
            .model(modelName)
            .temperature(openaiOpt.getTemperature())
            .numCtx(OLLAMA_DEFAULT_CTX)
            .build();
      }
      case OllamaChatOptions ollamaOpt -> {
        if (ollamaOpt.getModel() == null || ollamaOpt.getModel().isBlank()) {
          yield OllamaChatOptions.builder()
              .model(ollamaChatModel)
              .temperature(ollamaOpt.getTemperature())
              .numCtx(OLLAMA_DEFAULT_CTX)
              .build();
        }
        yield ollamaOpt;
      }
      case null ->
          OllamaChatOptions.builder()
              .model(ollamaChatModel)
              .temperature(DEFAULT_TEMPERATURE)
              .numCtx(OLLAMA_DEFAULT_CTX)
              .build();
      default -> springOptions;
    };
  }

  /**
   * Resolves default options for non-Ollama providers when no options are provided.
   *
   * @param provider The resolved provider name.
   * @return Default {@link ChatOptions} for the provider.
   */
  public static ChatOptions resolveDefaultOptions(String provider) {
    return switch (provider.toLowerCase()) {
      case "openai" -> OpenAiChatOptions.builder().temperature(DEFAULT_TEMPERATURE).build();
      case "claude", "anthropic" ->
          AnthropicChatOptions.builder().temperature(DEFAULT_TEMPERATURE).build();
      case "gemini", "google" ->
          GoogleGenAiChatOptions.builder().temperature(DEFAULT_TEMPERATURE).build();
      default -> ChatOptions.builder().temperature(DEFAULT_TEMPERATURE).build();
    };
  }
}
