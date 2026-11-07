package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import com.krizaka.sovkora.core.domain.model.chat.PromptDraft;
import java.util.List;
import java.util.Optional;

/**
 * Compiles a request into the prompt a model receives, in the provider library's types the engines
 * never see (#121). Two steps, so the interceptors run between them on the engine's own types.
 */
public interface PromptAssembly {

  /**
   * The provider the request's own options name.
   *
   * @param options the request options, or {@code null}
   * @return the provider key, or {@code null} when the options name none
   */
  String providerOf(EngineChatOptions options);

  /**
   * Compiles the history, the current prompt (with its image, if any) and the provider's options.
   *
   * @param request the request
   * @param promptText the refined prompt text
   * @param image the image the user's own prompt carried, if any
   * @param provider the resolved provider
   * @param ollamaChatModel the active local chat model, when the provider is the local one
   * @return the draft
   */
  PromptDraft draft(
      InternalChatRequest request,
      String promptText,
      Optional<byte[]> image,
      String provider,
      String ollamaChatModel);

  /**
   * Assembles the prompt from the (intercepted) draft and the tools the turn may call.
   *
   * @param messages the messages after the interceptors
   * @param options the options after the interceptors
   * @param provider the resolved provider
   * @param tools the tool handles resolved for the turn, opaque to the core
   * @return the prepared prompt
   */
  PreparedPrompt assemble(
      List<EngineMessage> messages, EngineChatOptions options, String provider, List<?> tools);
}
