package com.krizaka.sovkora.adapter.springai.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.adapter.springai.spend.SpendBudgetExceededException;
import com.krizaka.sovkora.adapter.springai.spend.SpendGuard;
import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class GuardedChatModelTest {

  private final AtomicInteger reached = new AtomicInteger();
  private final ChatModel provider =
      new ChatModel() {
        @Override
        public ChatResponse call(Prompt prompt) {
          reached.incrementAndGet();
          return new ChatResponse(java.util.List.of(new Generation(new AssistantMessage("ok"))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
          reached.incrementAndGet();
          return Flux.just(call(prompt));
        }
      };

  private final SpendGuard tripped = new SpendGuard(0.0, 0.01, 0.0002, 0.60);

  @Test
  void aTrippedBudgetRefusesAPaidCallBeforeTheProviderIsReached() {
    ModelEndpoint paid = new ModelEndpoint("openai", "https://api.openai.com", "k", "m");
    GuardedChatModel model = new GuardedChatModel(provider, paid, tripped);
    assertThatThrownBy(() -> model.call(new Prompt("hi")))
        .isInstanceOf(SpendBudgetExceededException.class);
    assertThatThrownBy(() -> model.stream(new Prompt("hi")).blockLast())
        .isInstanceOf(SpendBudgetExceededException.class);
    assertThat(reached).hasValue(0);
  }

  @Test
  void aLocalModelIsStillServedWhenTheBudgetIsSpent() {
    ModelEndpoint local = new ModelEndpoint("ollama", "http://localhost:11434", null, "qwen");
    GuardedChatModel model = new GuardedChatModel(provider, local, tripped);
    assertThat(model.call(new Prompt("hi"))).isNotNull();
    assertThat(model.stream(new Prompt("hi")).blockLast()).isNotNull();
    assertThat(reached).hasValue(3);
  }
}
