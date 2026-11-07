package com.krizaka.sovkora.core.application.pipeline;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.application.ChatModelInvocation;
import com.krizaka.sovkora.core.application.SpringPreparedPrompt;
import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.event.ChatCompletedEvent;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import com.krizaka.sovkora.core.domain.ports.outbound.DomainEventPublisher;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class EngineStreamBridgeTest {

  @Mock private ChatModel chatModel;
  @Mock private EnginePipelineBridge pipelineBridge;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private DynamicPipelineExecutor orchestrationPipeline;
  @Mock private PromptContextInterceptor interceptor;

  @Mock
  private com.krizaka.sovkora.core.domain.ports.outbound.UserCredentialsProvider
      credentialsProvider;

  @Mock
  private com.krizaka.sovkora.core.application.routing.DynamicChatModelFactory<ChatModel>
      modelFactory;

  private EngineStreamBridge streamBridge;

  @BeforeEach
  void setUp() {
    streamBridge =
        new EngineStreamBridge(
            ChatModelInvocation.of(chatModel, modelFactory), pipelineBridge, credentialsProvider);
  }

  @Test
  void shouldStreamChatResponsesSuccessfully() {
    // Arrange
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    var prompt = new Prompt(PROMPT_HELLO, OllamaChatOptions.builder().build());
    var context =
        new EnginePipelineContext(
            PROVIDER_OLLAMA, CONV_1, PROMPT_HELLO, new SpringPreparedPrompt(prompt));

    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor)))
        .thenReturn(context);

    var mockOut1 = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOut1.getText()).thenReturn("chunk 1");
    var gen1 = new Generation(mockOut1, ChatGenerationMetadata.NULL);
    var response1 = new ChatResponse(List.of(gen1));

    var mockOut2 = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOut2.getText()).thenReturn("chunk 2");
    var gen2 = new Generation(mockOut2, ChatGenerationMetadata.NULL);
    var response2 = new ChatResponse(List.of(gen2));

    when(chatModel.stream(prompt)).thenReturn(Flux.just(response1, response2));

    // Act
    Flux<InternalChatResponse> resultFlux =
        streamBridge.createStream(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor), eventPublisher);

    List<InternalChatResponse> results = resultFlux.collectList().block();

    // Assert
    assertThat(results).isNotNull().hasSize(3);
    assertThat(results.getFirst().content()).isEqualTo("chunk 1");
    assertThat(results.getFirst().conversationId()).isEqualTo(CONV_1);
    assertThat(results.getFirst().metadata()).containsEntry("provider", PROVIDER_OLLAMA);

    assertThat(results.get(1).content()).isEqualTo("chunk 2");
    assertThat(results.get(1).conversationId()).isEqualTo(CONV_1);
    assertThat(results.get(1).metadata()).containsEntry("provider", PROVIDER_OLLAMA);
    // every chunk says the answer is still being verified; the last event carries the verdict
    assertThat(results.getFirst().metadata()).containsEntry("control.verdict", "PENDING");
    assertThat(results.get(2).content()).isEmpty();
    assertThat(results.get(2).metadata()).containsEntry("control.verdict", "NOT_CHECKED");

    verify(eventPublisher, times(1)).publish(any(ChatCompletedEvent.class));
    verify(interceptor, times(1)).postProcess(request, PROMPT_HELLO, "chunk 1chunk 2");
  }

  @Test
  void aToolTheEngineRanIsAnnouncedWithItsNameCallIdWhoAndFeatureOnly() {
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    var prompt = new Prompt(PROMPT_HELLO, OllamaChatOptions.builder().build());
    var context =
        new EnginePipelineContext(
            PROVIDER_OLLAMA,
            CONV_1,
            PROMPT_HELLO,
            new SpringPreparedPrompt(prompt).forFeature("agent.code.issue-to-pr"));
    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor)))
        .thenReturn(context);
    var ran = new com.krizaka.sovkora.core.domain.model.chat.ToolCall("c1", "lookup", "{\"q\":1}");
    var chunks =
        Flux.just(
            new com.krizaka.sovkora.core.domain.model.chat.ChatCompletion(
                "", null, List.of(), List.of(ran)),
            new com.krizaka.sovkora.core.domain.model.chat.ChatCompletion("answer", null));
    var bridge =
        new EngineStreamBridge(
            new com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation() {
              @Override
              public com.krizaka.sovkora.core.domain.model.chat.ChatCompletion call(
                  com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt p,
                  com.krizaka.sovkora.core.domain.model.chat.ChatRoute r) {
                throw new UnsupportedOperationException();
              }

              @Override
              public Flux<com.krizaka.sovkora.core.domain.model.chat.ChatCompletion> stream(
                  com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt p,
                  com.krizaka.sovkora.core.domain.model.chat.ChatRoute r) {
                return chunks;
              }
            },
            pipelineBridge,
            credentialsProvider);

    bridge
        .createStream(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor), eventPublisher)
        .collectList()
        .block();

    var announced =
        org.mockito.ArgumentCaptor.forClass(
            com.krizaka.sovkora.core.domain.event.ToolExecutedEvent.class);
    verify(eventPublisher).publish(announced.capture());
    var event = announced.getValue();
    assertThat(event.conversationId()).isEqualTo(CONV_1);
    assertThat(event.toolName()).isEqualTo("lookup");
    assertThat(event.callId()).isEqualTo("c1");
    assertThat(event.userId()).isEqualTo(request.context().userId());
    assertThat(event.featureKey()).isEqualTo("agent.code.issue-to-pr");
    assertThat(event.durationMs()).isNotNegative();
    assertThat(event.outcome()).isEqualTo("ok");
    // never the arguments: nothing of {"q":1} is in the event
    assertThat(event.toString()).doesNotContain("\"q\"");
  }

  @Test
  void shouldFallbackToNonToolStreamOnSynchronousException() {
    // Arrange
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    var prompt = new Prompt(PROMPT_HELLO, OllamaChatOptions.builder().build());
    var context =
        new EnginePipelineContext(
            PROVIDER_OLLAMA, CONV_1, PROMPT_HELLO, new SpringPreparedPrompt(prompt));

    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor)))
        .thenReturn(context);

    // Sync throw of IllegalStateException
    when(chatModel.stream(prompt)).thenThrow(new IllegalStateException("Tool activation failed"));

    var mockOutFallback = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOutFallback.getText()).thenReturn("fallback chunk");
    var gen = new Generation(mockOutFallback, ChatGenerationMetadata.NULL);
    var responseFallback = new ChatResponse(List.of(gen));

    // Fallback stream when input is not prompt
    when(chatModel.stream((Prompt) argThat(p -> p != prompt)))
        .thenReturn(Flux.just(responseFallback));

    // Act
    Flux<InternalChatResponse> resultFlux =
        streamBridge.createStream(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor), eventPublisher);

    List<InternalChatResponse> results = resultFlux.collectList().block();

    // Assert
    assertThat(results).isNotNull().hasSize(2);
    assertThat(results.getFirst().content()).isEqualTo("fallback chunk");

    verify(eventPublisher, times(1)).publish(any(ChatCompletedEvent.class));
  }

  @Test
  void shouldFallbackToNonToolStreamOnAsynchronousException() {
    // Arrange
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    var prompt = new Prompt(PROMPT_HELLO, OllamaChatOptions.builder().build());
    var context =
        new EnginePipelineContext(
            PROVIDER_OLLAMA, CONV_1, PROMPT_HELLO, new SpringPreparedPrompt(prompt));

    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor)))
        .thenReturn(context);

    // Async emission of IllegalStateException
    when(chatModel.stream(prompt))
        .thenReturn(Flux.error(new IllegalStateException("Tool activation async failed")));

    var mockOutFallback = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOutFallback.getText()).thenReturn("fallback chunk async");
    var gen = new Generation(mockOutFallback, ChatGenerationMetadata.NULL);
    var responseFallback = new ChatResponse(List.of(gen));

    // Fallback stream when input is not prompt
    when(chatModel.stream((Prompt) argThat(p -> p != prompt)))
        .thenReturn(Flux.just(responseFallback));

    // Act
    Flux<InternalChatResponse> resultFlux =
        streamBridge.createStream(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor), eventPublisher);

    List<InternalChatResponse> results = resultFlux.collectList().block();

    // Assert
    assertThat(results).isNotNull().hasSize(2);
    assertThat(results.getFirst().content()).isEqualTo("fallback chunk async");

    verify(eventPublisher, times(1)).publish(any(ChatCompletedEvent.class));
  }

  @Test
  void shouldStreamWithCommercialModelWhenApiKeyIsFound() {
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    // Use OpenAiChatOptions to cover OpenAi path in option extraction
    var options = OpenAiChatOptions.builder().model("gpt-4o").build();
    var prompt = new Prompt(PROMPT_HELLO, options);
    var context =
        new EnginePipelineContext("openai", CONV_1, PROMPT_HELLO, new SpringPreparedPrompt(prompt));

    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, "openai", List.of(interceptor)))
        .thenReturn(context);

    // Mock decrypted API key
    String userId = request.context().userId();
    when(credentialsProvider.getDecryptedApiKey(userId, "openai"))
        .thenReturn(Optional.of("sk-mock-key"));

    // Mock custom chat model from factory
    ChatModel mockOpenAiModel = mock(ChatModel.class);
    when(modelFactory.createChatModel("openai", "gpt-4o", "sk-mock-key"))
        .thenReturn(mockOpenAiModel);

    // Mock stream output
    var mockOut = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOut.getText()).thenReturn("openai chunk");
    var gen = new Generation(mockOut, ChatGenerationMetadata.NULL);
    var response = new ChatResponse(List.of(gen));
    when(mockOpenAiModel.stream(prompt)).thenReturn(Flux.just(response));

    // Act
    Flux<InternalChatResponse> resultFlux =
        streamBridge.createStream(
            request, orchestrationPipeline, "openai", List.of(interceptor), eventPublisher);

    List<InternalChatResponse> results = resultFlux.collectList().block();

    // Assert
    assertThat(results).isNotNull().hasSize(2);
    assertThat(results.getFirst().content()).isEqualTo("openai chunk");
    assertThat(results.getFirst().metadata()).containsEntry("provider", "openai");
  }

  @Test
  void shouldFallbackToLocalModelWhenCommercialApiKeyIsMissing() {
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    var prompt = new Prompt(PROMPT_HELLO, OllamaChatOptions.builder().build());
    var context =
        new EnginePipelineContext("openai", CONV_1, PROMPT_HELLO, new SpringPreparedPrompt(prompt));

    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, "openai", List.of(interceptor)))
        .thenReturn(context);

    // Mock missing key
    String userId = request.context().userId();
    when(credentialsProvider.getDecryptedApiKey(userId, "openai")).thenReturn(Optional.empty());

    var mockOut = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOut.getText()).thenReturn("local fallback chunk");
    var gen = new Generation(mockOut, ChatGenerationMetadata.NULL);
    var response = new ChatResponse(List.of(gen));
    when(chatModel.stream(prompt)).thenReturn(Flux.just(response));

    // Act
    Flux<InternalChatResponse> resultFlux =
        streamBridge.createStream(
            request, orchestrationPipeline, "openai", List.of(interceptor), eventPublisher);

    List<InternalChatResponse> results = resultFlux.collectList().block();

    // Assert
    assertThat(results).isNotNull().hasSize(2);
    assertThat(results.getFirst().content()).isEqualTo("local fallback chunk");
  }

  @Test
  void shouldExtractModelNamesForAnthropicAndGoogleOptions() {
    // 1. Anthropic Options
    var anthropicOptions = AnthropicChatOptions.builder().model("claude-3").build();
    var anthropicPrompt = new Prompt(PROMPT_HELLO, anthropicOptions);
    var anthropicContext =
        new EnginePipelineContext(
            "anthropic", CONV_1, PROMPT_HELLO, new SpringPreparedPrompt(anthropicPrompt));

    // 2. Google Options
    var googleOptions = GoogleGenAiChatOptions.builder().model("gemini-pro").build();
    var googlePrompt = new Prompt(PROMPT_HELLO, googleOptions);
    var googleContext =
        new EnginePipelineContext(
            "google", CONV_1, PROMPT_HELLO, new SpringPreparedPrompt(googlePrompt));

    // Let's verify we hit createChatModel with correct resolved model names
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    String userId = request.context().userId();

    when(credentialsProvider.getDecryptedApiKey(userId, "anthropic"))
        .thenReturn(Optional.of("mock-key"));
    when(credentialsProvider.getDecryptedApiKey(userId, "google"))
        .thenReturn(Optional.of("mock-key"));

    ChatModel mockAnthropicModel = mock(ChatModel.class);
    ChatModel mockGoogleModel = mock(ChatModel.class);

    when(modelFactory.createChatModel("anthropic", "claude-3", "mock-key"))
        .thenReturn(mockAnthropicModel);
    when(modelFactory.createChatModel("google", "gemini-pro", "mock-key"))
        .thenReturn(mockGoogleModel);

    // Stub streams
    var mockOut = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOut.getText()).thenReturn("chunk");
    var response = new ChatResponse(List.of(new Generation(mockOut, ChatGenerationMetadata.NULL)));
    when(mockAnthropicModel.stream(anthropicPrompt)).thenReturn(Flux.just(response));
    when(mockGoogleModel.stream(googlePrompt)).thenReturn(Flux.just(response));

    // Test Anthropic compilation path
    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, "anthropic", List.of(interceptor)))
        .thenReturn(anthropicContext);
    streamBridge
        .createStream(
            request, orchestrationPipeline, "anthropic", List.of(interceptor), eventPublisher)
        .collectList()
        .block();

    // Test Google compilation path
    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, "google", List.of(interceptor)))
        .thenReturn(googleContext);
    streamBridge
        .createStream(
            request, orchestrationPipeline, "google", List.of(interceptor), eventPublisher)
        .collectList()
        .block();

    verify(modelFactory).createChatModel("anthropic", "claude-3", "mock-key");
    verify(modelFactory).createChatModel("google", "gemini-pro", "mock-key");
  }

  /**
   * The bridge sees the prompt and the whole completion; its logs may carry neither, not even at
   * DEBUG (a log keeps no data class, ADR-064). A source guard: no log statement of the bridge
   * mentions the prompt's instructions, the prompt text or the assembled completion.
   */
  @Test
  void shouldNeverLogWhatThePersonSaidEvenAtDebug() throws Exception {
    var source =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/krizaka/sovkora/core/application/pipeline/EngineStreamBridge.java"));
    var statements =
        java.util.regex.Pattern.compile("logger\\.\\w+\\((?:[^;]|\\n)*?;").matcher(source);
    int seen = 0;
    while (statements.find()) {
      seen++;
      assertThat(statements.group())
          .as("log statement must not carry content")
          .doesNotContain(
              "getInstructions()", "promptText", "fullText", "responseBuilder", "getText");
    }
    assertThat(seen).isPositive();
  }
}
