package com.krizaka.sovkora.adapter.springai.prompt;

import com.krizaka.sovkora.core.application.routing.ProviderClassifier;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Reads the provider and the model out of Spring AI's {@link ChatOptions}, the only place that
 * knows the vendor option types. The provider names come from {@link ProviderClassifier}.
 */
final class SpringAiChatOptionsInfo {

  private SpringAiChatOptionsInfo() {}

  /**
   * @param options the chat options (nullable)
   * @return the provider identifier, or {@code null} if unresolvable
   */
  static String providerOf(ChatOptions options) {
    return switch (options) {
      case OllamaChatOptions ignored -> ProviderClassifier.ollama();
      case OpenAiChatOptions ignored -> ProviderClassifier.openai();
      case AnthropicChatOptions ignored -> ProviderClassifier.anthropic();
      case GoogleGenAiChatOptions ignored -> ProviderClassifier.gemini();
      case null, default -> null;
    };
  }

  /**
   * @param options the chat options (nullable)
   * @return the model name, or {@code null} if the options type is unrecognized or null
   */
  static String modelNameOf(ChatOptions options) {
    return switch (options) {
      case OpenAiChatOptions o -> o.getModel();
      case AnthropicChatOptions a -> a.getModel();
      case GoogleGenAiChatOptions g -> g.getModel();
      case OllamaChatOptions ol -> ol.getModel();
      case null, default -> null;
    };
  }
}
