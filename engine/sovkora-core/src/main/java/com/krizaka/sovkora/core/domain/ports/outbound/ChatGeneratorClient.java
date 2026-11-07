package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.chat.ChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.ChatResponse;
import reactor.core.publisher.Flux;

/** Port interface for executing chat generation inference against AI chat models. */
public interface ChatGeneratorClient {

  /**
   * Generates a chat response from the chat request parameters.
   *
   * @param request The chat request payload.
   * @return The generated chat response.
   */
  ChatResponse generateChat(ChatRequest request);

  /**
   * Generates a streaming chat response from the chat request parameters.
   *
   * @param request The chat request payload.
   * @return A reactive token stream of chat responses.
   */
  Flux<ChatResponse> streamChat(ChatRequest request);
}
