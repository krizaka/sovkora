package com.krizaka.sovkora.adapter.springai.prompt;

import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * The prompt the assembly builds, in Spring AI's type: the engines hold a {@link PreparedPrompt},
 * only the chat invocation adapter opens this.
 *
 * @param prompt the assembled prompt
 * @param featureKey the feature of the turn, or {@code null}
 * @param sensitive whether the turn is SENSITIVE: never cached by a provider
 */
public record SpringPreparedPrompt(Prompt prompt, String featureKey, boolean sensitive)
    implements PreparedPrompt {

  /** A prompt of a turn with a feature key, not sensitive. */
  public SpringPreparedPrompt(Prompt prompt, String featureKey) {
    this(prompt, featureKey, false);
  }

  /** A prompt of a turn with no feature key. */
  public SpringPreparedPrompt(Prompt prompt) {
    this(prompt, null);
  }

  @Override
  public PreparedPrompt forFeature(String key) {
    return new SpringPreparedPrompt(prompt, key, sensitive);
  }

  @Override
  public PreparedPrompt asSensitive() {
    return new SpringPreparedPrompt(prompt, featureKey, true);
  }

  @Override
  public int messageCount() {
    return prompt.getInstructions().size();
  }

  @Override
  public String modelName() {
    return SpringAiChatOptionsInfo.modelNameOf(prompt.getOptions());
  }

  @Override
  public PreparedPrompt withoutTools() {
    return new SpringPreparedPrompt(removeTools(prompt), featureKey, sensitive);
  }

  /**
   * Strips tool definitions from a prompt, keeping the model, temperature and token limit.
   *
   * @param prompt the prompt
   * @return the prompt with clean options
   */
  public static Prompt removeTools(Prompt prompt) {
    ChatOptions options = prompt.getOptions();
    if (options == null) {
      return prompt;
    }
    ChatOptions cleanOptions =
        switch (options) {
          case OllamaChatOptions ollama ->
              OllamaChatOptions.builder()
                  .model(ollama.getModel())
                  .temperature(ollama.getTemperature())
                  .numPredict(ollama.getNumPredict())
                  .numCtx(8192)
                  .build();
          case OpenAiChatOptions openai ->
              OpenAiChatOptions.builder()
                  .model(openai.getModel())
                  .temperature(openai.getTemperature())
                  .maxTokens(openai.getMaxTokens())
                  .build();
          default -> options;
        };
    return new Prompt(prompt.getInstructions(), cleanOptions);
  }
}
