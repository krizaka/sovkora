package com.krizaka.sovkora.core.domain.model.chat;

/**
 * The settings of one model call as the engine sees them, in pure Java (ADR-092 §4 rule 1).
 * Interceptors read and return this type, never a model library's.
 *
 * <p>Options that came from a library keep it in {@code nativeOptions} (provider-specific settings,
 * tool definitions) so that leaving the engine loses nothing; only the adapter at the edge reads it
 * back. Code outside that edge treats it as opaque.
 *
 * @param model the model name, or {@code null} for the default
 * @param temperature the sampling temperature, or {@code null}
 * @param maxTokens the completion cap, or {@code null}
 * @param nativeOptions the library options these stand for, or {@code null}
 */
public record EngineChatOptions(
    String model, Double temperature, Integer maxTokens, Object nativeOptions) {

  /** Settings with no library behind them. */
  public EngineChatOptions(String model, Double temperature, Integer maxTokens) {
    this(model, temperature, maxTokens, null);
  }

  /** The same settings standing for other library options. */
  public EngineChatOptions withNativeOptions(Object options) {
    return new EngineChatOptions(model, temperature, maxTokens, options);
  }
}
