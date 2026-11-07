package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.chat.ChatCompletion;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import reactor.core.publisher.Flux;

/**
 * The port that sends a prepared prompt to a model (#121): the engines call it and never hold the
 * provider library's model, prompt or response. Its Spring AI implementation lives in {@code
 * sovkora-adapter-spring-ai}.
 */
public interface ChatInvocation {

  /**
   * Sends the prompt and waits for the whole answer.
   *
   * @param prompt the prompt
   * @param route the model that answers
   * @return the answer
   */
  ChatCompletion call(PreparedPrompt prompt, ChatRoute route);

  /**
   * Sends the prompt and streams the answer; the usage arrives on the last chunk that carries it.
   *
   * @param prompt the prompt
   * @param route the model that answers
   * @return the chunks
   */
  Flux<ChatCompletion> stream(PreparedPrompt prompt, ChatRoute route);
}
