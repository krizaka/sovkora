package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Port (ADR-092 §4): one plain text question to a model chosen by its provider key, for the
 * interceptors that ask a model about the turn (refine, route, judge, adjust) and read a text back.
 * The implementation lives in {@code sovkora-adapter-spring-ai}; no model library type crosses it.
 */
public interface ProviderChatClient {

  /**
   * The provider keys that can be asked.
   *
   * @return the keys, possibly empty
   */
  Set<String> providers();

  /**
   * Finds the registered provider a configured name stands for: the key itself, else the first key
   * that contains the name (ignoring case).
   *
   * @param requested the configured provider name
   * @return the registered key, or empty when none matches
   */
  Optional<String> resolve(String requested);

  /**
   * Asks the model of a provider.
   *
   * @param provider the provider whose model answers; the options are built for the name given to
   *     {@code optionsProvider}
   * @param optionsProvider the provider the options (model, temperature) are built for
   * @param messages the conversation, in order
   * @param model the model name, or {@code null} for the provider's default
   * @param temperature the sampling temperature, or {@code null}
   * @return the raw text of the answer, or {@code null} when the model said nothing
   * @throws IllegalArgumentException when no options can be built for {@code optionsProvider}
   */
  String complete(
      String provider,
      String optionsProvider,
      List<EngineMessage> messages,
      String model,
      Double temperature);

  /**
   * Asks the first registered model, with only a temperature as setting.
   *
   * @param messages the conversation, in order
   * @param temperature the sampling temperature, or {@code null} for the model's own
   * @return the raw text of the answer, or {@code null} when the model said nothing
   * @throws IllegalStateException when no model is registered
   */
  String completeWithDefault(List<EngineMessage> messages, Double temperature);
}
