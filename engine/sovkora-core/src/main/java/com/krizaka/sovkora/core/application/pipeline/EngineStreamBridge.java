package com.krizaka.sovkora.core.application.pipeline;

import com.krizaka.sovkora.core.application.engine.AbstractEngine;
import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.event.ChatCompletedEvent;
import com.krizaka.sovkora.core.domain.event.ToolExecutedEvent;
import com.krizaka.sovkora.core.domain.model.CachedResponse;
import com.krizaka.sovkora.core.domain.model.chat.ChatCompletion;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatResponse;
import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.core.domain.model.chat.ToolCall;
import com.krizaka.sovkora.core.domain.model.control.ControlMetadata;
import com.krizaka.sovkora.core.domain.model.control.ControlState;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation;
import com.krizaka.sovkora.core.domain.ports.outbound.DomainEventPublisher;
import com.krizaka.sovkora.core.domain.ports.outbound.UserCredentialsProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Component streaming bridge for the Sovkora AI engine.
 *
 * <p>Creates a deferred {@link Flux} of {@link InternalChatResponse} chunks for real-time token
 * streaming. Handles tool execution fallback and post-completion event publishing.
 *
 * <p>The stream lifecycle:
 *
 * <ol>
 *   <li>Compiles the inference context via {@link EnginePipelineBridge#compileContext}
 *   <li>Opens a streaming connection to the resolved {@link ChatModel}
 *   <li>Falls back to tool-stripped streaming on {@link IllegalStateException} (sync or async)
 *   <li>Accumulates all chunks in a {@link StringBuilder} for post-processing
 *   <li>Publishes an {@link ChatCompletedEvent} with the full assembled text on completion
 * </ol>
 *
 * @see AbstractEngine#stream(InternalChatRequest)
 * @see EnginePipelineBridge
 */
public final class EngineStreamBridge {

  private static final Logger logger = LoggerFactory.getLogger(EngineStreamBridge.class);
  private static final String PROVIDER_KEY = "provider";

  private final ChatInvocation chatInvocation;
  private final EnginePipelineBridge pipelineBridge;
  private final UserCredentialsProvider credentialsProvider;

  /** Constructs the streaming bridge. */
  public EngineStreamBridge(
      ChatInvocation chatInvocation,
      EnginePipelineBridge pipelineBridge,
      UserCredentialsProvider credentialsProvider) {
    this.chatInvocation = Objects.requireNonNull(chatInvocation, "ChatInvocation must not be null");
    this.pipelineBridge =
        Objects.requireNonNull(pipelineBridge, "EnginePipelineBridge must not be null");
    this.credentialsProvider =
        Objects.requireNonNull(credentialsProvider, "UserCredentialsProvider must not be null");
  }

  /**
   * Creates a deferred reactive stream of chat response chunks.
   *
   * <p>The {@code Flux.defer()} wrapper ensures that context compilation and model resolution
   * happen lazily at subscription time, not at assembly time.
   *
   * @param request The incoming internal chat request.
   * @param pipeline The orchestration pipeline (nullable — bypassed if null).
   * @param defaultProvider The default provider name.
   * @param interceptors Ordered interceptors for pre/post-processing.
   * @param eventPublisher Event publisher for {@link ChatCompletedEvent}.
   * @return A {@link Flux} emitting incremental {@link InternalChatResponse} chunks.
   */
  public Flux<InternalChatResponse> createStream(
      InternalChatRequest request,
      DynamicPipelineExecutor pipeline,
      String defaultProvider,
      List<PromptContextInterceptor> interceptors,
      DomainEventPublisher eventPublisher) {

    return Flux.defer(
        () -> {
          var context =
              pipelineBridge.compileContext(request, pipeline, defaultProvider, interceptors);
          if (context.cachedResponse() != null) {
            return servedFromCache(request, context, interceptors, eventPublisher);
          }
          var responseBuilder = new StringBuilder();
          long startTime = System.nanoTime();
          // Usage arrives once, on the last chunk of a stream — captured here so the completion
          // handler can settle the turn's hold against real tokens rather than an estimate.
          final AtomicReference<TokenUsage> tokenUsage = new AtomicReference<>(TokenUsage.none());
          final List<ToolCall> askedFor = new java.util.concurrent.CopyOnWriteArrayList<>();

          ChatRoute route =
              ChatRoutes.resolve(
                  request.context().userId(), context.provider(), credentialsProvider);
          // Never the prompt, not even at DEBUG: a log keeps no data class (ADR-064).
          Flux<ChatCompletion> streamFlux = initStream(request, route, context);

          return streamFlux
              .onErrorResume(
                  RuntimeException.class,
                  ex -> {
                    if (request.clientToolMode()) {
                      return Flux.error(ex);
                    }
                    logger.warn(
                        "Asynchronous tool execution activation failure, falling back to non-tool text inference stream.",
                        ex);
                    return chatInvocation.stream(context.toPrompt().withoutTools(), route);
                  })
              .doOnNext(
                  completion ->
                      announceExecuted(completion, request, context, startTime, eventPublisher))
              .map(
                  completion -> {
                    String chunk = completion.text();
                    if (chunk != null) {
                      responseBuilder.append(chunk);
                    }
                    // The deterministic screens read what is assembled before the chunk leaves: a
                    // refusal stops the stream here and the chunk is never emitted (#125).
                    ResponseScreen.screenChunk(
                        interceptors, context.promptContext(), responseBuilder.toString());
                    captureTokenUsage(completion, tokenUsage);
                    // Screened once, here: what the client gets is what passed ADR-091 this turn.
                    List<ToolCall> returned =
                        request.clientToolMode()
                            ? pipelineBridge.screenClientCalls(request, completion.toolCalls())
                            : List.of();
                    askedFor.addAll(returned);
                    return new InternalChatResponse(
                        chunk,
                        context.conversationId(),
                        TokenUsage.none(),
                        Map.of(
                            PROVIDER_KEY,
                            context.provider(),
                            ControlMetadata.VERDICT.key(),
                            ControlState.PENDING.name()),
                        returned);
                  })
              .concatWith(
                  Flux.defer(
                      () ->
                          Flux.just(
                              finish(
                                  request,
                                  context,
                                  interceptors,
                                  eventPublisher,
                                  new Streamed(
                                      responseBuilder.toString(),
                                      tokenUsage.get(),
                                      List.copyOf(askedFor)),
                                  startTime))))
              .doFinally(signalType -> closeResources(context));
        });
  }

  /** What a streamed turn assembled: the whole text, the usage and the tool calls it asked for. */
  private record Streamed(String text, TokenUsage usage, List<ToolCall> askedFor) {}

  /**
   * The end of a streamed turn: the semantic tiers screen the assembled answer, then, only if it is
   * accepted, the completion event is published and the final event carries the verdict. A refusal
   * here errors the stream before any final event (sovkora-engine#125).
   */
  private static InternalChatResponse finish(
      InternalChatRequest request,
      EnginePipelineContext context,
      List<PromptContextInterceptor> interceptors,
      DomainEventPublisher eventPublisher,
      Streamed streamed,
      long startTime) {
    var screened =
        ResponseScreen.screenStreamed(interceptors, context.promptContext(), streamed.text());
    interceptors.forEach(i -> i.postProcess(request, context.promptText(), streamed.text()));
    Map<String, Object> metadata = buildCompletionMetadata(context, startTime);
    metadata.putAll(screened.notes());
    metadata.putIfAbsent(ControlMetadata.VERDICT.key(), ControlState.NOT_CHECKED.name());
    eventPublisher.publish(
        new ChatCompletedEvent(
            request,
            new InternalChatResponse(
                streamed.text(),
                context.conversationId(),
                streamed.usage(),
                Map.copyOf(metadata),
                streamed.askedFor()),
            context.holdId()));
    // The text already left in the chunks: the final event repeats none of it.
    return new InternalChatResponse("", context.conversationId(), Map.copyOf(metadata));
  }

  /** A cached answer as a stream of one screened chunk, then the completion event (no usage). */
  private static Flux<InternalChatResponse> servedFromCache(
      InternalChatRequest request,
      EnginePipelineContext context,
      List<PromptContextInterceptor> interceptors,
      DomainEventPublisher eventPublisher) {
    var cached = context.cachedResponse();
    var screened = ResponseScreen.screen(interceptors, context.promptContext(), cached.text());
    String text = screened.text();
    Map<String, Object> metadata = new HashMap<>(screened.notes());
    metadata.put(PROVIDER_KEY, CachedResponse.PROVIDER);
    metadata.put("cache", cached.tier());
    metadata.put("cacheSimilarity", cached.similarity());
    var chunk = new InternalChatResponse(text, context.conversationId(), metadata);
    return Flux.just(chunk)
        .doOnComplete(
            () ->
                eventPublisher.publish(
                    new ChatCompletedEvent(
                        request,
                        new InternalChatResponse(
                            text, context.conversationId(), TokenUsage.none(), metadata),
                        context.holdId())));
  }

  private Flux<ChatCompletion> initStream(
      InternalChatRequest request, ChatRoute route, EnginePipelineContext context) {
    try {
      return chatInvocation.stream(context.toPrompt(), route);
    } catch (RuntimeException e) {
      if (request.clientToolMode()) {
        throw e;
      }
      logger.warn(
          "Model '{}' stream failed tool execution activation, falling back to non-tool text inference stream.",
          context.provider(),
          e);
      return chatInvocation.stream(context.toPrompt().withoutTools(), route);
    }
  }

  /** Says, once per call, which tools the engine ran during the turn (name and id only). */
  private static void announceExecuted(
      ChatCompletion completion,
      InternalChatRequest request,
      EnginePipelineContext context,
      long startNanos,
      DomainEventPublisher publisher) {
    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
    for (ToolCall ran : completion.executed()) {
      publisher.publish(
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

  /** Keeps the last usage a chunk carried; most chunks carry none and must not erase it. */
  private static void captureTokenUsage(
      ChatCompletion completion, AtomicReference<TokenUsage> tokenUsage) {
    if (completion != null && completion.usage().reported()) {
      tokenUsage.set(completion.usage());
    }
  }

  private static Map<String, Object> buildCompletionMetadata(
      EnginePipelineContext context, long startTime) {
    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(PROVIDER_KEY, context.provider());
    metadata.put("durationMs", durationMs);
    return metadata;
  }

  private static void closeResources(EnginePipelineContext context) {
    if (context.closeables() == null) {
      return;
    }
    for (AutoCloseable c : context.closeables()) {
      try {
        c.close();
      } catch (Exception e) {
        logger.error("Failed to close dynamic MCP client", e);
      }
    }
  }
}
