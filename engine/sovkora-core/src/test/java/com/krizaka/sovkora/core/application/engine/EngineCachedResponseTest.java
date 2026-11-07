package com.krizaka.sovkora.core.application.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.application.ChatModelInvocation;
import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.DynamicPipelineExecutor;
import com.krizaka.sovkora.core.application.pipeline.EnginePipelineBridge;
import com.krizaka.sovkora.core.application.pipeline.EngineStreamBridge;
import com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.core.application.routing.DynamicChatModelFactory;
import com.krizaka.sovkora.core.domain.event.ChatCompletedEvent;
import com.krizaka.sovkora.core.domain.model.CachedResponse;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import com.krizaka.sovkora.core.domain.ports.outbound.DomainEventPublisher;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.sovkora.core.domain.ports.outbound.UserCredentialsProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.UserMcpServerProvider;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * A turn answered by a cache: the model is not called, the hold is not billed, and the answer still
 * passes the output screens, exactly as a fresh one does. A cached answer is not a refusal.
 */
@ExtendWith(MockitoExtension.class)
class EngineCachedResponseTest {

  @Mock private ChatModel chatModel;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private ModelCatalogProvider modelCatalogProvider;
  @Mock private UserCredentialsProvider credentialsProvider;
  @Mock private DynamicChatModelFactory<ChatModel> modelFactory;
  @Mock private PlatformMcpServerProvider platformMcpServerProvider;
  @Mock private UserMcpServerProvider userMcpServerProvider;
  @Mock private ToolRegistry toolRegistry;
  @Mock private DynamicPipelineExecutor pipeline;

  private final List<String> screened = new ArrayList<>();
  private final List<String> postProcessed = new ArrayList<>();
  private final List<String> served = new ArrayList<>();

  /** What a cache needs: to hear about a fresh answer once it is served. */
  private class Listener implements PromptContextInterceptor {
    @Override
    public void responseServed(PromptContext context, String servedText) {
      served.add(servedText);
    }
  }

  /** An output screen that records what it saw, and may rewrite or refuse. */
  private class Screen implements PromptContextInterceptor {
    private final String refuseWhenContains;
    private final String replacement;

    Screen(String refuseWhenContains, String replacement) {
      this.refuseWhenContains = refuseWhenContains;
      this.replacement = replacement;
    }

    @Override
    public String screenResponse(PromptContext context, String responseText) {
      screened.add(responseText);
      if (refuseWhenContains != null && responseText.contains(refuseWhenContains)) {
        throw new PipelineShortCircuitException("Screen", "output_refused", "reviewed text", null);
      }
      return replacement != null ? replacement : responseText;
    }

    @Override
    public void postProcess(InternalChatRequest request, String promptText, String responseText) {
      postProcessed.add(responseText);
    }
  }

  private Engine engine(PromptContextInterceptor... interceptors) {
    var properties =
        new CoreProperties(
            "ollama",
            new CoreProperties.RagConfig(false, null, 3),
            new CoreProperties.McpConfig(List.of()),
            new CoreProperties.OrchestrationConfig(
                false,
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.InterceptorConfig(null, null, 0.0),
                new CoreProperties.RoutingConfig(null, null)),
            null,
            null,
            null,
            null);
    var bridge =
        new EnginePipelineBridge(
            modelCatalogProvider,
            request ->
                com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution.ResolvedTools.none(),
            new com.krizaka.sovkora.core.application.TestPromptAssembly());
    var streamBridge =
        new EngineStreamBridge(
            ChatModelInvocation.of(chatModel, modelFactory), bridge, credentialsProvider);
    return new Engine(
        ChatModelInvocation.of(chatModel, modelFactory),
        null,
        properties,
        List.of(interceptors),
        pipeline,
        eventPublisher,
        modelCatalogProvider,
        bridge,
        streamBridge,
        credentialsProvider);
  }

  @BeforeEach
  void quietCatalogue() {
    lenient()
        .when(modelCatalogProvider.getActiveChatModel())
        .thenReturn(Optional.of("llama3.1:8b"));
  }

  private void pipelineAnswers(CachedResponse cached) {
    var base = new PromptContext("hello", Map.of("userId", "u-1")).withBillingHold("hold-7");
    when(pipeline.process(any(), anyInt(), any()))
        .thenReturn(cached == null ? base : base.withCachedResponse(cached));
  }

  private void modelAnswers(String text) {
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
  }

  @Test
  void aCachedTurnIsAnsweredWithoutCallingTheModel() {
    pipelineAnswers(new CachedResponse("Hi from cache", "SEMANTIC", 0.97));

    InternalChatResponse out = engine().chat(InternalChatRequest.simple("hello"));

    assertThat(out.content()).isEqualTo("Hi from cache");
    assertThat(out.metadata())
        .containsEntry("cache", "SEMANTIC")
        .containsEntry("provider", "cache");
    verifyNoInteractions(chatModel);
  }

  @Test
  void aCachedTurnReleasesItsHoldByReportingNoUsage() {
    pipelineAnswers(new CachedResponse("Hi", "EXACT", 1.0));

    engine().chat(InternalChatRequest.simple("hello"));

    var event = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publish(event.capture());
    var completed = (ChatCompletedEvent) event.getValue();
    assertThat(completed.holdId()).isEqualTo("hold-7");
    assertThat(completed.response().tokenUsage().reported()).isFalse();
  }

  @Test
  void aCachedAnswerPassesTheOutputScreenLikeAFreshOne() {
    pipelineAnswers(new CachedResponse("cached text", "SEMANTIC", 0.99));
    var out = engine(new Screen(null, "screened text")).chat(InternalChatRequest.simple("hello"));

    assertThat(screened).containsExactly("cached text");
    assertThat(out.content()).isEqualTo("screened text");
  }

  @Test
  void aCachedAnswerTheScreenRefusesIsRefusedAndNeverServed() {
    pipelineAnswers(new CachedResponse("cached self-harm text", "EXACT", 1.0));
    var engine = engine(new Screen("self-harm", null));

    assertThrows(
        PipelineShortCircuitException.class,
        () -> engine.chat(InternalChatRequest.simple("hello")));
    verify(eventPublisher, never()).publish(any(Object.class));
  }

  @Test
  void aFreshAnswerPassesTheSameScreen() {
    pipelineAnswers(null);
    modelAnswers("fresh text");

    var out = engine(new Screen(null, "screened fresh")).chat(InternalChatRequest.simple("hello"));

    assertThat(screened).containsExactly("fresh text");
    assertThat(out.content()).isEqualTo("screened fresh");
  }

  @Test
  void aFreshAnswerTheScreenRefusesIsNotServed() {
    pipelineAnswers(null);
    modelAnswers("fresh self-harm text");
    var engine = engine(new Screen("self-harm", null));

    assertThrows(
        PipelineShortCircuitException.class,
        () -> engine.chat(InternalChatRequest.simple("hello")));
  }

  @Test
  void aCachedTurnSkipsThePostProcessingOfAFreshOne() {
    pipelineAnswers(new CachedResponse("cached", "EXACT", 1.0));
    engine(new Screen(null, null)).chat(InternalChatRequest.simple("hello"));
    assertThat(postProcessed).isEmpty();
  }

  @Test
  void aCachedStreamIsOneScreenedChunk() {
    pipelineAnswers(new CachedResponse("streamed from cache", "EXACT", 1.0));

    var chunks =
        engine(new Screen(null, null)).stream(InternalChatRequest.simple("hello"))
            .collectList()
            .block();

    assertThat(chunks).hasSize(1);
    assertThat(chunks.getFirst().content()).isEqualTo("streamed from cache");
    assertThat(screened).containsExactly("streamed from cache");
    verifyNoInteractions(chatModel);
    verify(eventPublisher, times(1)).publish(any(ChatCompletedEvent.class));
  }

  @Test
  void aFreshAnswerIsAnnouncedOnceServedAfterTheScreens() {
    pipelineAnswers(null);
    modelAnswers("fresh text");

    engine(new Screen(null, "screened"), new Listener()).chat(InternalChatRequest.simple("hello"));

    assertThat(served).containsExactly("screened");
  }

  @Test
  void aRefusedFreshAnswerIsNeverAnnounced() {
    pipelineAnswers(null);
    modelAnswers("fresh self-harm text");
    var engine = engine(new Screen("self-harm", null), new Listener());

    assertThrows(
        PipelineShortCircuitException.class,
        () -> engine.chat(InternalChatRequest.simple("hello")));
    assertThat(served).isEmpty();
  }

  @Test
  void aCachedAnswerIsNeverAnnouncedAgain() {
    pipelineAnswers(new CachedResponse("cached", "EXACT", 1.0));
    engine(new Listener()).chat(InternalChatRequest.simple("hello"));
    assertThat(served).isEmpty();
  }
}
