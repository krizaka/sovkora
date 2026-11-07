package com.krizaka.sovkora.core.domain.model.chat;

/**
 * A turn's prompt, compiled and ready to be sent to a model: what the engines hold in place of the
 * provider library's own prompt type (#121). Only the {@link
 * com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation} adapter reads its content.
 */
public interface PreparedPrompt {

  /**
   * How many messages the prompt carries (a count for logs, never their content).
   *
   * @return the count
   */
  int messageCount();

  /**
   * The model the prompt asks for.
   *
   * @return the model name, or {@code null} when the options name none
   */
  String modelName();

  /**
   * The same prompt without its tool definitions, for the fallback when tool activation fails.
   *
   * @return the prompt without tools
   */
  PreparedPrompt withoutTools();

  /**
   * The feature the turn belongs to (ADR-029), which names its tool-round bound.
   *
   * @return the feature key, or {@code null} for a turn with none
   */
  default String featureKey() {
    return null;
  }

  /**
   * The same prompt, carrying the feature of its turn.
   *
   * @param featureKey the feature key, or {@code null}
   * @return the prompt for that feature
   */
  default PreparedPrompt forFeature(String featureKey) {
    return this;
  }

  /**
   * Whether the turn is SENSITIVE ({@link TurnSensitivity}): such a prompt is never cached by a
   * provider.
   *
   * @return true for a sensitive turn
   */
  default boolean sensitive() {
    return false;
  }

  /**
   * The same prompt, marked as a sensitive turn.
   *
   * @return the sensitive prompt
   */
  default PreparedPrompt asSensitive() {
    return this;
  }
}
