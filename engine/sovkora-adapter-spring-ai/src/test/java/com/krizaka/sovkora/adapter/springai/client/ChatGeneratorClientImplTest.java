package com.krizaka.sovkora.adapter.springai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.application.engine.Engine;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.ChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.ChatResponse;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class ChatGeneratorClientImplTest {

  private Engine engine;
  private ChatGeneratorClientImpl client;

  @BeforeEach
  void setUp() {
    engine = mock(Engine.class);
    client = new ChatGeneratorClientImpl(engine);
  }

  @Test
  void constructorRejectsNullEngine() {
    assertThatThrownBy(() -> new ChatGeneratorClientImpl(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void generateChat_delegatesToEngineAndMapsResponse() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Hello");
    var request = new ChatRequest("Hello", List.of(msg), Map.of(), ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("Hi there", "conv-1", Map.of()));

    ChatResponse response = client.generateChat(request);

    assertThat(response.content()).isEqualTo("Hi there");
    assertThat(response.conversationId()).isEqualTo("conv-1");
    verify(engine).chat(any(InternalChatRequest.class));
  }

  @Test
  void generateChat_withOllamaSettings() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Test");
    var settings =
        Map.<String, Object>of("provider", "ollama", "model", "llama3", "temperature", 0.7);
    var request = new ChatRequest("Test", List.of(msg), settings, ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("OK", "conv-2", Map.of()));

    ChatResponse response = client.generateChat(request);
    assertThat(response.content()).isEqualTo("OK");
  }

  @Test
  void generateChat_withOpenAiSettings() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Test");
    var settings =
        Map.<String, Object>of("provider", "openai", "model", "gpt-4", "temperature", 0.5);
    var request = new ChatRequest("Test", List.of(msg), settings, ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("Done", "conv-3", Map.of()));

    ChatResponse response = client.generateChat(request);
    assertThat(response.content()).isEqualTo("Done");
  }

  @Test
  void generateChat_withNullSettings() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Hi");
    var request = new ChatRequest("Hi", List.of(msg), null, ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("Response", null, Map.of()));

    ChatResponse response = client.generateChat(request);
    assertThat(response.content()).isEqualTo("Response");
  }

  @Test
  void generateChat_withEmptySettings() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Hi");
    var request = new ChatRequest("Hi", List.of(msg), Map.of(), ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("R", null, Map.of()));

    ChatResponse response = client.generateChat(request);
    assertThat(response.content()).isEqualTo("R");
  }

  @Test
  void generateChat_withUnknownProvider() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Hi");
    var settings = Map.<String, Object>of("provider", "anthropic", "model", "claude-3");
    var request = new ChatRequest("Hi", List.of(msg), settings, ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("R", null, Map.of()));

    ChatResponse response = client.generateChat(request);
    assertThat(response).isNotNull();
  }

  @Test
  void streamChat_delegatesToEngineStream() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Stream me");
    var request = new ChatRequest("Stream me", List.of(msg), Map.of(), ctx);

    when(engine.stream(any(InternalChatRequest.class)))
        .thenReturn(
            Flux.just(
                new InternalChatResponse("chunk1", "conv-s", Map.of()),
                new InternalChatResponse("chunk2", "conv-s", Map.of())));

    List<ChatResponse> responses = client.streamChat(request).collectList().block();

    assertThat(responses).hasSize(2);
    assertThat(responses.getFirst().content()).isEqualTo("chunk1");
    assertThat(responses.get(1).content()).isEqualTo("chunk2");
  }

  /**
   * A model is routed to the provider the catalogue binds it to (RoutedChatModel), so a request
   * naming only a model must keep it: without a "provider" setting it used to be dropped and the
   * turn went to the default model (serverless) — qwen2.5:7b never reached Ollama.
   */
  @Test
  void generateChat_withModelOnly_keepsTheModel() {
    var request =
        new ChatRequest(
            "Hi",
            List.of(new ChatRequest.ChatMessage("user", "Hi")),
            Map.<String, Object>of("model", "qwen2.5:7b", "temperature", 0.2),
            Context.anonymous());
    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("R", null, Map.of()));

    client.generateChat(request);

    var sent = org.mockito.ArgumentCaptor.forClass(InternalChatRequest.class);
    org.mockito.Mockito.verify(engine).chat(sent.capture());
    assertThat(sent.getValue().options().model()).isEqualTo("qwen2.5:7b");
    assertThat(sent.getValue().options().temperature()).isEqualTo(0.2);
  }

  @Test
  void generateChat_withAnyOtherProvider_keepsTheModel() {
    var request =
        new ChatRequest(
            "Hi",
            List.of(new ChatRequest.ChatMessage("user", "Hi")),
            Map.<String, Object>of("provider", "do-inference", "model", "deepseek-v4-pro"),
            Context.anonymous());
    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("R", null, Map.of()));

    client.generateChat(request);

    var sent = org.mockito.ArgumentCaptor.forClass(InternalChatRequest.class);
    org.mockito.Mockito.verify(engine).chat(sent.capture());
    assertThat(sent.getValue().options().model()).isEqualTo("deepseek-v4-pro");
  }
}
