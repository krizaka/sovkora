package com.krizaka.sovkora.core.application.engine;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.ChatRoutes;
import com.krizaka.sovkora.core.application.pipeline.DynamicPipelineExecutor;
import com.krizaka.sovkora.core.application.pipeline.EnginePipelineBridge;
import com.krizaka.sovkora.core.application.pipeline.EnginePipelineContext;
import com.krizaka.sovkora.core.application.pipeline.EngineStreamBridge;
import com.krizaka.sovkora.core.application.pipeline.ResponseScreen;
import com.krizaka.sovkora.core.domain.event.ChatCompletedEvent;
import com.krizaka.sovkora.core.domain.event.ToolExecutedEvent;
import com.krizaka.sovkora.core.domain.model.CachedResponse;
import com.krizaka.sovkora.core.domain.model.audio.AudioRequest;
import com.krizaka.sovkora.core.domain.model.chat.ChatCompletion;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.core.domain.model.chat.ToolCall;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation;
import com.krizaka.sovkora.core.domain.ports.outbound.DomainEventPublisher;
import com.krizaka.sovkora.core.domain.ports.outbound.SpeechSynthesis;
import com.krizaka.sovkora.core.infrastructure.support.CoreException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Sealed abstract base class for the Sovkora AI orchestration engine.
 *
 * <p>This class defines the contract for all multi-modal AI operations — text chat, image
 * generation, speech synthesis, and token streaming. It is the <b>central inference backbone</b> of
 * {@code sovkora-core}, encapsulating:
 *
 * <ul>
 *   <li>Direct Spring AI model interfaces
 *   <li>Context-Matrix orchestration pipeline integration via {@link DynamicPipelineExecutor}
 *   <li>Pre/post-processing interceptor chain via {@link ContextInterceptor}
 *   <li>Domain event publishing for completed chat interactions
 * </ul>
 *
 * <p>The class is {@code sealed} to restrict the engine hierarchy to the single production subclass
 * {@link Engine}, enforcing exhaustive pattern-matching safety (ADR-003).
 *
 * <p><b>Concurrency</b>: All public methods are designed to execute inside Java 21 Virtual Threads
 * per the concurrency mandate in {@code AGENTS.md}. No {@code synchronized} blocks are used over
 * I/O loops.
 *
 * @see Engine
 */
public abstract sealed class AbstractEngine permits Engine {

  private static final Logger logger = LoggerFactory.getLogger(AbstractEngine.class);

  /** The port that sends a prepared prompt to a model. */
  protected final ChatInvocation chatInvocation;

  /** The port that turns text into speech audio. */
  protected final SpeechSynthesis speech;

  /** Default provider name. */
  protected final String defaultProvider;

  /** Ordered list of context interceptors applied before and after inference. */
  final List<PromptContextInterceptor> interceptors;

  /** The orchestration pipeline (refiner, router, system/user context resolvers). */
  final DynamicPipelineExecutor pipeline;

  /** Spring application event publisher for domain events. */
  final DomainEventPublisher eventPublisher;

  /** Grouped infrastructure dependencies (pipeline bridge, stream bridge, credentials, etc.). */
  protected final EngineInfrastructure infra;

  /**
   * Constructs the engine with primary Spring AI models and pipeline components.
   *
   * @param chatInvocation The port that sends a prepared prompt to a model.
   * @param speech The port that turns text into speech audio.
   * @param defaultProvider Default provider name.
   * @param interceptors Ordered list of context interceptors (nullable — defaults to empty).
   * @param pipeline The orchestration pipeline (nullable — bypassed if null).
   * @param publisher Event publisher for {@link ChatCompletedEvent}.
   * @param infra Grouped infrastructure dependencies.
   */
  protected AbstractEngine(
      ChatInvocation chatInvocation,
      SpeechSynthesis speech,
      String defaultProvider,
      List<PromptContextInterceptor> interceptors,
      DynamicPipelineExecutor pipeline,
      DomainEventPublisher publisher,
      EngineInfrastructure infra) {
    this.chatInvocation = chatInvocation;
    this.speech = speech;
    this.defaultProvider = defaultProvider;
    this.interceptors = interceptors != null ? List.copyOf(interceptors) : List.of();
    this.pipeline = pipeline;
    this.eventPublisher = publisher;
    this.infra = infra;
  }

  /**
   * Executes a synchronous (non-streaming) chat inference.
   *
   * <p>Processing flow:
   *
   * <ol>
   *   <li>Compiles the full context via {@link EnginePipelineBridge#compileContext}
   *   <li>Invokes the resolved {@link ChatModel} with the assembled prompt
   *   <li>Falls back to tool-stripped inference on {@link IllegalStateException}
   *   <li>Runs all post-processing interceptors
   *   <li>Publishes an {@link ChatCompletedEvent}
   * </ol>
   *
   * @param request The internal chat request containing prompt, context, and options.
   * @return The engine's response with generated text, conversation ID, and metadata.
   * @throws CoreException If no suitable ChatModel is found for the resolved provider.
   */
  public InternalChatResponse chat(InternalChatRequest request) {
    var context =
        infra.pipelineBridge().compileContext(request, pipeline, defaultProvider, interceptors);
    if (context.cachedResponse() != null) {
      return serveCached(request, context);
    }
    // What is logged is the call's shape, never its content: prompts and responses were written
    // here
    // at INFO, for every turn of every data class, to a file with no retention and no audit — the
    // two things a SENSITIVE turn is owed (ADR-064).
    logger.info(
        "Engine executing chat model call: provider={}, conversation={}, messages={}",
        context.provider(),
        context.conversationId(),
        context.toPrompt().messageCount());
    ChatRoute route =
        ChatRoutes.resolve(
            request.context().userId(), context.provider(), infra.credentialsProvider());

    ChatCompletion response;
    long startTime = System.nanoTime();
    try {
      response = chatInvocation.call(context.toPrompt(), route);
      logger.info("Engine chat model call returned for conversation {}", context.conversationId());
    } catch (RuntimeException e) {
      if (request.clientToolMode()) {
        // A turn that cannot offer its tools is a failed turn: answering in text without them would
        // tell the client the model had nothing to call.
        throw e;
      }
      logger.warn(
          "Model '{}' failed tool execution activation, falling back to non-tool text inference.",
          context.provider(),
          e);
      response = chatInvocation.call(context.toPrompt().withoutTools(), route);
      logger.info(
          "Engine fallback chat model call returned for conversation {}", context.conversationId());
    } finally {
      closeContextResources(context);
    }
    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
    if (response != null) {
      for (ToolCall ran : response.executed()) {
        eventPublisher.publish(
            new ToolExecutedEvent(
                context.conversationId(),
                ran.name(),
                ran.id(),
                request.context().userId(),
                context.toPrompt().featureKey(),
                durationMs,
                ToolExecutedEvent.OUTCOME_OK));
      }
    }
    // Every answer is screened before it leaves — this one and a cached one take the same path.
    var screened =
        ResponseScreen.screen(interceptors, context.promptContext(), extractResponseText(response));
    String text = screened.text();
    ResponseScreen.served(interceptors, context.promptContext(), text);

    interceptors.forEach(i -> i.postProcess(request, context.promptText(), text));

    Map<String, Object> metadata = buildChatMetadata(context, durationMs);
    metadata.putAll(screened.notes());
    var out =
        new InternalChatResponse(
            text,
            context.conversationId(),
            extractUsage(response),
            Map.copyOf(metadata),
            clientCalls(request, response));
    eventPublisher.publish(new ChatCompletedEvent(request, out, context.holdId()));
    return out;
  }

  /**
   * Serves an answer a cache found before inference: no model call, no usage reported (the turn's
   * hold is released, not billed), the same output screens as a fresh answer.
   */
  private InternalChatResponse serveCached(
      InternalChatRequest request, EnginePipelineContext context) {
    var cached = context.cachedResponse();
    logger.info(
        "Engine answered from cache: tier={}, conversation={}",
        cached.tier(),
        context.conversationId());
    var screened = ResponseScreen.screen(interceptors, context.promptContext(), cached.text());
    String text = screened.text();
    Map<String, Object> metadata = new HashMap<>(screened.notes());
    metadata.put("provider", CachedResponse.PROVIDER);
    metadata.put("cache", cached.tier());
    metadata.put("cacheSimilarity", cached.similarity());
    metadata.put("durationMs", 0L);
    var out =
        new InternalChatResponse(
            text, context.conversationId(), TokenUsage.none(), Map.copyOf(metadata));
    eventPublisher.publish(new ChatCompletedEvent(request, out, context.holdId()));
    return out;
  }

  /** The calls of the model that go back to the client; none outside client-executed mode. */
  private List<ToolCall> clientCalls(InternalChatRequest request, ChatCompletion response) {
    if (!request.clientToolMode() || response == null) {
      return List.of();
    }
    return infra.pipelineBridge().screenClientCalls(request, response.toolCalls());
  }

  /** Extracts the text of a completion, returning empty string when there is none. */
  private String extractResponseText(ChatCompletion response) {
    if (response == null || response.text() == null) {
      logger.warn(
          "Chat model response, result, or output was null. Falling back to default empty response.");
      return "";
    }
    return response.text();
  }

  /** Builds metadata map from context and timing information. */
  private static Map<String, Object> buildChatMetadata(
      EnginePipelineContext context, long durationMs) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("provider", context.provider());
    metadata.put("durationMs", durationMs);
    return metadata;
  }

  /**
   * Reads what the provider says the turn consumed.
   *
   * <p>A provider that reports nothing yields {@link TokenUsage#none()} rather than zeros: an
   * unmeasured turn releases its hold, and zeros would bill it at the pricebook's floor instead.
   */
  private static TokenUsage extractUsage(ChatCompletion response) {
    return response == null ? TokenUsage.none() : response.usage();
  }

  /** Closes any dynamic MCP client resources from the inference context. */
  private void closeContextResources(EnginePipelineContext context) {
    if (context.closeables() != null) {
      for (AutoCloseable c : context.closeables()) {
        try {
          c.close();
        } catch (Exception e) {
          logger.error("Failed to close dynamic MCP client", e);
        }
      }
    }
  }

  /**
   * Creates a reactive token stream for real-time chat inference.
   *
   * <p>Delegates stream assembly to {@link EngineStreamBridge#createStream}, which manages the
   * deferred Flux lifecycle, tool fallback, and post-completion event publishing.
   *
   * @param request The internal chat request containing prompt, context, and options.
   * @return A {@link Flux} emitting incremental {@link InternalChatResponse} chunks.
   */
  public Flux<InternalChatResponse> stream(InternalChatRequest request) {
    return infra
        .streamBridge()
        .createStream(request, pipeline, defaultProvider, interceptors, eventPublisher);
  }

  /**
   * Generates speech audio from text using the active TTS model.
   *
   * @param request The audio request containing the text and user context.
   * @return The raw audio bytes (MP3 format).
   * @throws CoreException If no SpeechSynthesis is registered for the active provider.
   */
  public byte[] generateSpeech(AudioRequest request) {
    return speech.synthesize(request.prompt(), request.model(), request.responseFormat());
  }
}
