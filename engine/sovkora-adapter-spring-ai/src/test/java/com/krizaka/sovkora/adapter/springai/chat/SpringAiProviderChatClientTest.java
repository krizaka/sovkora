package com.krizaka.sovkora.adapter.springai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.adapter.springai.prompt.PipelineOptionsRegistry;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiProviderChatClientTest {

  private final ChatModel ollama = mock(ChatModel.class);
  private final ChatModel openai = mock(ChatModel.class);
  private final PipelineOptionsRegistry registry = mock(PipelineOptionsRegistry.class);

  private SpringAiProviderChatClient client() {
    Map<String, ChatModel> models = new LinkedHashMap<>();
    models.put("ollama", ollama);
    models.put("openai", openai);
    return new SpringAiProviderChatClient(models, registry);
  }

  private static ChatResponse reply(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  @Test
  void resolve_prefersTheExactKeyThenAKeyContainingTheName() {
    assertThat(client().resolve("openai")).contains("openai");
    assertThat(client().resolve("OPEN")).contains("openai");
    assertThat(client().resolve("nothing")).isEmpty();
    assertThat(client().providers()).containsExactlyInAnyOrder("ollama", "openai");
  }

  @Test
  void complete_asksTheProvidersModelWithOptionsBuiltForTheNamedProvider() {
    ChatOptions options = ChatOptions.builder().model("gpt-4").temperature(0.5).build();
    when(registry.build("open", "gpt-4", 0.5)).thenReturn(options);
    when(openai.call(any(Prompt.class))).thenReturn(reply(" refined "));

    String text =
        client()
            .complete(
                "openai",
                "open",
                List.of(EngineMessage.system("rules"), EngineMessage.user("question")),
                "gpt-4",
                0.5);

    assertThat(text).isEqualTo(" refined ");
    var prompt = ArgumentCaptor.forClass(Prompt.class);
    org.mockito.Mockito.verify(openai).call(prompt.capture());
    assertThat(prompt.getValue().getOptions()).isSameAs(options);
    assertThat(prompt.getValue().getInstructions())
        .satisfiesExactly(
            m -> assertThat(m).isInstanceOf(SystemMessage.class),
            m -> assertThat(m).isInstanceOf(UserMessage.class));
    assertThat(prompt.getValue().getContents()).contains("rules").contains("question");
  }

  @Test
  void complete_withAnUnregisteredProvider_fails() {
    assertThatThrownBy(() -> client().complete("nope", "nope", List.of(), null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void completeWithDefault_usesTheFirstRegisteredModelAndOnlyTheTemperatureAsSetting() {
    when(ollama.call(any(Prompt.class))).thenReturn(reply("answer"));

    String text = client().completeWithDefault(List.of(EngineMessage.user("hi")), 0.0);

    assertThat(text).isEqualTo("answer");
    var prompt = ArgumentCaptor.forClass(Prompt.class);
    org.mockito.Mockito.verify(ollama).call(prompt.capture());
    assertThat(prompt.getValue().getOptions().getTemperature()).isEqualTo(0.0);
  }

  @Test
  void completeWithDefault_withoutATemperatureSendsNoOptions() {
    when(ollama.call(any(Prompt.class))).thenReturn(reply("answer"));

    client().completeWithDefault(List.of(EngineMessage.user("hi")), null);

    var prompt = ArgumentCaptor.forClass(Prompt.class);
    org.mockito.Mockito.verify(ollama).call(prompt.capture());
    assertThat(prompt.getValue().getOptions()).isNull();
  }

  @Test
  void completeWithDefault_withoutAnyModel_fails() {
    var empty = new SpringAiProviderChatClient(Map.of(), registry);

    assertThatThrownBy(() -> empty.completeWithDefault(List.of(), null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void complete_returnsNullWhenTheModelSaysNothing() {
    when(registry.build("ollama", null, null)).thenReturn(ChatOptions.builder().build());
    when(ollama.call(any(Prompt.class))).thenReturn(null);

    assertThat(client().complete("ollama", "ollama", List.of(), null, null)).isNull();
  }
}
