package com.krizaka.sovkora.adapter.springai.routing;

import com.krizaka.sovkora.adapter.springai.spend.SpendGuard;
import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/** A {@link ChatModel} whose every call first passes the {@link SpendGuard}. */
public final class GuardedChatModel implements ChatModel {

  private final ChatModel delegate;
  private final ModelEndpoint endpoint;
  private final SpendGuard guard;

  public GuardedChatModel(ChatModel delegate, ModelEndpoint endpoint, SpendGuard guard) {
    this.delegate = delegate;
    this.endpoint = endpoint;
    this.guard = guard;
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    guard.authorize(SpendGuard.Kind.CHAT, endpoint);
    return delegate.call(prompt);
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    return Flux.defer(
        () -> {
          guard.authorize(SpendGuard.Kind.CHAT, endpoint);
          return delegate.stream(prompt);
        });
  }

  @Override
  public ChatOptions getDefaultOptions() {
    return delegate.getDefaultOptions();
  }
}
