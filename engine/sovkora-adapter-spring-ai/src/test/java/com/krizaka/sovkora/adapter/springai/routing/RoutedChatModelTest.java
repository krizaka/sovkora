package com.krizaka.sovkora.adapter.springai.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.StepModelUnavailableException;
import com.krizaka.sovkora.core.infrastructure.provider.model.ModelUnavailableException;
import com.openai.core.http.Headers;
import com.openai.errors.InternalServerException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import reactor.core.publisher.Flux;

/**
 * Guards the ADR-030 option-normalisation contract. The universal proxy ({@code
 * UniversalProxyChatProvider}) always resolves an {@code OpenAiChatModel}, whose {@code
 * createRequest} casts the prompt's runtime options to {@link OpenAiChatOptions}. So {@link
 * RoutedChatModel} must coerce upstream {@code OllamaChatOptions} to {@code OpenAiChatOptions} —
 * without that conversion chat throws a {@code ClassCastException}, and a naive conversion NPEs on
 * the nullable {@code getToolCallbacks()} (the two regressions these tests pin down).
 */
class RoutedChatModelTest {

  @Test
  void toOpenAiPrompt_convertsOllamaOptions_andDoesNotNpeWhenToolCallbacksNull() {
    // OllamaChatOptions.getToolCallbacks() returns null (not an empty list): the regression case.
    Prompt prompt =
        new Prompt("hi", OllamaChatOptions.builder().model("llama3.2:3b").temperature(0.7).build());

    Prompt result = RoutedChatModel.toOpenAiPrompt(prompt);

    assertThat(result.getOptions()).isInstanceOf(OpenAiChatOptions.class);
    assertThat(result.getOptions().getModel()).isEqualTo("llama3.2:3b");
    assertThat(result.getOptions().getTemperature()).isEqualTo(0.7);
  }

  @Test
  void toOpenAiPrompt_preservesToolCallbacksWhenPresent() {
    ToolCallback tool = mock(ToolCallback.class);
    Prompt prompt =
        new Prompt(
            "hi", OllamaChatOptions.builder().model("m").toolCallbacks(List.of(tool)).build());

    OpenAiChatOptions result =
        (OpenAiChatOptions) RoutedChatModel.toOpenAiPrompt(prompt).getOptions();

    assertThat(result.getToolCallbacks()).containsExactly(tool);
  }

  @Test
  void toOpenAiPrompt_passesOpenAiOptionsThroughUnchanged() {
    Prompt prompt = new Prompt("hi", OpenAiChatOptions.builder().model("gpt-4o").build());

    assertThat(RoutedChatModel.toOpenAiPrompt(prompt)).isSameAs(prompt);
  }

  @Test
  void stream_routesToRequestedModel_andForwardsOpenAiOptions() {
    DynamicModelRouter router = mock(DynamicModelRouter.class);
    ChatModel delegate = mock(ChatModel.class);
    when(delegate.stream(any(Prompt.class))).thenReturn(Flux.empty());
    when(router.chatModel("llama3.2:3b")).thenReturn(delegate);
    RoutedChatModel routed = new RoutedChatModel(router);

    routed.stream(new Prompt("hi", OllamaChatOptions.builder().model("llama3.2:3b").build()))
        .blockLast();

    verify(router).chatModel("llama3.2:3b");
    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    verify(delegate).stream(captor.capture());
    assertThat(captor.getValue().getOptions()).isInstanceOf(OpenAiChatOptions.class);
  }

  @Test
  void stream_routesToDefaultModel_whenPromptHasNoOptions() {
    DynamicModelRouter router = mock(DynamicModelRouter.class);
    ChatModel delegate = mock(ChatModel.class);
    when(delegate.stream(any(Prompt.class))).thenReturn(Flux.empty());
    when(router.defaultChatModel(any())).thenReturn(delegate);
    RoutedChatModel routed = new RoutedChatModel(router);

    routed.stream(new Prompt("hi")).blockLast();

    verify(router).defaultChatModel(any());
  }

  @Test
  void call_routesToRequestedModel_andForwardsOpenAiOptions() {
    DynamicModelRouter router = mock(DynamicModelRouter.class);
    ChatModel delegate = mock(ChatModel.class);
    when(delegate.call(any(Prompt.class))).thenReturn(mock(ChatResponse.class));
    when(router.chatModel("llama3.2:3b")).thenReturn(delegate);
    RoutedChatModel routed = new RoutedChatModel(router);

    routed.call(new Prompt("hi", OllamaChatOptions.builder().model("llama3.2:3b").build()));

    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    verify(delegate).call(captor.capture());
    assertThat(captor.getValue().getOptions()).isInstanceOf(OpenAiChatOptions.class);
  }

  @Test
  void call_unreachableModelRaisesThePortableFallbackSignal() {
    DynamicModelRouter router = mock(DynamicModelRouter.class);
    ChatModel delegate = mock(ChatModel.class);
    when(router.chatModel("primary")).thenReturn(delegate);
    when(delegate.call(any(Prompt.class)))
        .thenThrow(new RuntimeException(new java.net.ConnectException("Connection refused")));

    org.junit.jupiter.api.Assertions.assertThrows(
        StepModelUnavailableException.class,
        () ->
            new RoutedChatModel(router)
                .call(new Prompt("hi", OpenAiChatOptions.builder().model("primary").build())));
  }

  @Test
  void call_doesNotClassifyAnApplicationFailureAsModelUnavailability() {
    DynamicModelRouter router = mock(DynamicModelRouter.class);
    ChatModel delegate = mock(ChatModel.class);
    var failure = new IllegalArgumentException("Invalid request");
    when(router.chatModel("primary")).thenReturn(delegate);
    when(delegate.call(any(Prompt.class))).thenThrow(failure);

    assertThat(
            org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                    new RoutedChatModel(router)
                        .call(
                            new Prompt(
                                "hi", OpenAiChatOptions.builder().model("primary").build()))))
        .isSameAs(failure);
  }

  @ParameterizedTest
  @ValueSource(ints = {502, 503, 504})
  void temporaryProviderFailureAllowsFallback(int status) {
    var failure = new HttpServerErrorException(HttpStatus.valueOf(status));

    assertThat(RoutedChatModel.isUnreachable(failure)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {502, 503, 504})
  void sdkProviderFailureAllowsFallback(int status) {
    var failure =
        InternalServerException.builder()
            .statusCode(status)
            .headers(Headers.builder().build())
            .build();

    assertThat(RoutedChatModel.isUnreachable(new RuntimeException(failure))).isTrue();
  }

  @Test
  void sdkInternalErrorDoesNotTriggerUnavailabilityFallback() {
    var failure =
        InternalServerException.builder()
            .statusCode(500)
            .headers(Headers.builder().build())
            .build();

    assertThat(RoutedChatModel.isUnreachable(failure)).isFalse();
  }

  @Test
  void timeoutsAllowFallbackButAuthenticationAndBillingDoNot() {
    assertThat(RoutedChatModel.isUnreachable(new java.net.SocketTimeoutException())).isTrue();
    assertThat(
            RoutedChatModel.isUnreachable(
                new org.springframework.web.client.HttpClientErrorException(
                    HttpStatus.UNAUTHORIZED)))
        .isFalse();
    assertThat(
            RoutedChatModel.isUnreachable(
                new org.springframework.web.client.HttpClientErrorException(
                    HttpStatus.PAYMENT_REQUIRED)))
        .isFalse();
  }

  /**
   * The default route must name the catalogue default. {@code OpenAiChatOptions.builder()} fills
   * Spring AI's own default model ({@code gpt-5-mini}); converted options without a model would
   * send it to whatever provider the catalogue default lives on — 403 on DigitalOcean inference
   * (dev.sovkora.com, 2026-10-15).
   */
  @Test
  void stream_defaultRoute_sendsTheCatalogueDefaultModel() {
    DynamicModelRouter router = mock(DynamicModelRouter.class);
    ChatModel delegate = mock(ChatModel.class);
    when(delegate.stream(any(Prompt.class))).thenReturn(Flux.empty());
    when(router.defaultChatModel(any())).thenReturn(delegate);
    when(router.defaultModelName(any())).thenReturn("mistral-3-14B");
    RoutedChatModel routed = new RoutedChatModel(router);

    routed.stream(
            new Prompt(
                "hi",
                org.springframework.ai.chat.prompt.ChatOptions.builder().temperature(0.7).build()))
        .blockLast();

    ArgumentCaptor<Prompt> sent = ArgumentCaptor.forClass(Prompt.class);
    verify(delegate).stream(sent.capture());
    assertThat(sent.getValue().getOptions().getModel()).isEqualTo("mistral-3-14B");
    assertThat(sent.getValue().getOptions().getTemperature()).isEqualTo(0.7);
  }

  @Test
  void call_defaultRoute_withoutOptions_sendsTheCatalogueDefaultModel() {
    DynamicModelRouter router = mock(DynamicModelRouter.class);
    ChatModel delegate = mock(ChatModel.class);
    when(router.defaultChatModel(any())).thenReturn(delegate);
    when(router.defaultModelName(any())).thenReturn("mistral-3-14B");

    new RoutedChatModel(router).call(new Prompt("hi"));

    ArgumentCaptor<Prompt> sent = ArgumentCaptor.forClass(Prompt.class);
    verify(delegate).call(sent.capture());
    assertThat(sent.getValue().getOptions().getModel()).isEqualTo("mistral-3-14B");
  }

  /**
   * A local model (Ollama behind the GPU relay) with no worker connected fails with a message that
   * says so — not a bare "Connection refused".
   */
  @Test
  void stream_localModelWithoutWorker_failsWithAClearError() {
    DynamicModelRouter router = mock(DynamicModelRouter.class);
    ChatModel delegate = mock(ChatModel.class);
    when(delegate.stream(any(Prompt.class)))
        .thenReturn(
            Flux.error(
                new RuntimeException(
                    "Request failed", new java.net.ConnectException("Connection refused"))));
    when(router.chatModel("qwen2.5:7b")).thenReturn(delegate);
    when(router.describe("qwen2.5:7b")).thenReturn("provider 'ollama' at http://gpu-relay:11434");

    var options = OpenAiChatOptions.builder().model("qwen2.5:7b").build();
    Throwable error =
        org.junit.jupiter.api.Assertions.assertThrows(
            ModelUnavailableException.class,
            () -> new RoutedChatModel(router).stream(new Prompt("hi", options)).blockLast());

    assertThat(error.getMessage())
        .contains("qwen2.5:7b")
        .contains("provider 'ollama' at http://gpu-relay:11434")
        .contains("no worker");
  }
}
