package com.krizaka.sovkora.core.application.pipeline;

import com.krizaka.sovkora.core.application.engine.AbstractEngine;
import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.routing.ProviderClassifier;
import com.krizaka.sovkora.core.domain.model.CachedResponse;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import com.krizaka.sovkora.core.domain.model.chat.PromptDraft;
import com.krizaka.sovkora.core.domain.model.chat.ToolCall;
import com.krizaka.sovkora.core.domain.model.chat.TurnSensitivity;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.PromptAssembly;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution;
import com.krizaka.sovkora.core.infrastructure.support.CoreException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Component bridge that compiles a complete inference context from a raw chat request.
 *
 * <p>Orchestrates the pre-inference pipeline by delegating to:
 *
 * <ul>
 *   <li>{@link DynamicPipelineExecutor} — refiner → router → context resolvers
 *   <li>{@link ChatOptionsResolver} — provider-specific options mapping
 *   <li>{@link ToolResolution} — the tools of the turn (local and MCP)
 *   <li>{@link MessageCompiler} — chat message history compilation
 * </ul>
 *
 * @see EnginePipelineContext
 * @see AbstractEngine
 */
public final class EnginePipelineBridge {

  private final ModelCatalogProvider modelCatalogProvider;
  private final ToolResolution toolResolution;
  private final PromptAssembly promptAssembly;

  /**
   * Constructs the bridge.
   *
   * @param modelCatalogProvider The outbound model catalog provider.
   * @param toolResolution The port that resolves the tools of a turn.
   * @param promptAssembly The port that compiles the prompt in the provider library's types.
   */
  public EnginePipelineBridge(
      ModelCatalogProvider modelCatalogProvider,
      ToolResolution toolResolution,
      PromptAssembly promptAssembly) {
    this.modelCatalogProvider =
        Objects.requireNonNull(modelCatalogProvider, "ModelCatalogProvider must not be null");
    this.toolResolution = Objects.requireNonNull(toolResolution, "ToolResolution must not be null");
    this.promptAssembly = Objects.requireNonNull(promptAssembly, "PromptAssembly must not be null");
  }

  /**
   * Compiles a full inference context from the raw request through the pipeline.
   *
   * @param request The incoming internal chat request.
   * @param pipeline The dynamic pipeline executor (nullable — bypassed if null).
   * @param defaultProvider The default provider name.
   * @param interceptors Ordered interceptors for pre-processing the prompt and options.
   * @return A fully compiled {@link EnginePipelineContext} ready for model invocation.
   * @throws CoreException If the resolved provider is empty or target model is missing.
   */
  public EnginePipelineContext compileContext(
      InternalChatRequest request,
      DynamicPipelineExecutor pipeline,
      String defaultProvider,
      List<PromptContextInterceptor> interceptors) {

    String originalPrompt = request.prompt();
    String cleanedRawQuery = originalPrompt != null ? originalPrompt : "";

    // Extract Base64 media with size validation (max 10 MB)
    Base64MediaExtractor.ExtractionResult mediaResult =
        Base64MediaExtractor.extract(cleanedRawQuery);
    String queryForPipeline = mediaResult.cleanedQuery();
    boolean hasMedia = mediaResult.imageBytes().isPresent();

    // Every turn goes through the pipeline, media or not (ADR-063). A branch here skipped it for a
    // prompt carrying media — and with it the orchestration switch, disable-ai, the crisis and
    // scope guards, and the entitlement check with its credit hold — for any caller who put an
    // image marker in the text. The pipeline never saw the base64 (it is lifted out above), so the
    // skip protected no interceptor; what the pipeline could not see was that an image was there,
    // and that is declared now instead of hidden.
    int mediaParts = hasMedia ? 1 : 0;
    PromptContext pipelineContext =
        Optional.ofNullable(pipeline)
            .map(p -> p.process(queryForPipeline, mediaParts, request.context()))
            .orElse(null);

    String promptText =
        Optional.ofNullable(pipelineContext)
            .map(PromptContext::refinedPrompt)
            .orElse(queryForPipeline);

    if (pipelineContext != null && pipelineContext.cachedResponse().isPresent()) {
      // Answered before inference: no provider, no messages, no MCP client to open.
      return new EnginePipelineContext(
          CachedResponse.PROVIDER,
          request.context().conversationId(),
          promptText,
          null,
          List.of(),
          resolveHoldId(pipelineContext),
          pipelineContext.cachedResponse().orElseThrow(),
          pipelineContext);
    }

    String provider =
        resolveProvider(
            pipelineContext, promptAssembly.providerOf(request.options()), defaultProvider);
    String ollamaChatModel = resolveOllamaChatModel(provider);

    PromptDraft draft =
        promptAssembly.draft(
            request, promptText, mediaResult.imageBytes(), provider, ollamaChatModel);

    // Interceptors speak the engine's own types; the library's stay behind the port.
    var engineMessages = draft.messages();
    var engineOptions =
        interceptors.stream()
            .reduce(
                draft.options(),
                (opts, interceptor) ->
                    interceptor.preProcess(request, promptText, engineMessages, opts),
                (o1, o2) -> o1);

    // The turn's tools, resolved behind the port (roles and denials audit are checked at the tool)
    // In client-executed mode (ADR-093 §4) the offer is the client's declared tools the actor may
    // use, re-checked on this turn; the engine's own tools are not offered at all.
    var tools =
        request.clientToolMode()
            ? toolResolution.resolveClientTools(request)
            : toolResolution.resolve(request);
    PreparedPrompt prompt =
        promptAssembly
            .assemble(engineMessages, engineOptions, provider, tools.handles())
            .forFeature(request.featureKey());
    if (pipelineContext != null && TurnSensitivity.of(pipelineContext.userMetadata())) {
      prompt = prompt.asSensitive();
    }
    String conversationId = request.context().conversationId();

    return new EnginePipelineContext(
        provider,
        conversationId,
        promptText,
        prompt,
        tools.closeables(),
        resolveHoldId(pipelineContext),
        null,
        pipelineContext);
  }

  /**
   * Screens the calls the model made in client-executed mode (ADR-093 §4): those whose tool was
   * declared and the actor may still use go back to the client; the rest are dropped and audited.
   *
   * @param request the chat request
   * @param calls what the model asked for
   * @return the calls to return to the client
   */
  public List<ToolCall> screenClientCalls(InternalChatRequest request, List<ToolCall> calls) {
    return calls.isEmpty() ? calls : toolResolution.screenClientCalls(request, calls);
  }

  /**
   * Lifts the credit reservation the gate recorded, so the completed turn can be settled against
   * it. The core relays an opaque string it never interprets — the interceptor that writes it and
   * the listener that settles it are the two ends that give it meaning, and both live outside.
   */
  private static String resolveHoldId(PromptContext pipelineContext) {
    return pipelineContext == null ? null : pipelineContext.billingHoldId();
  }

  /**
   * Resolves the target AI provider from pipeline routing, request options, or default.
   *
   * @param pipelineContext The result from the orchestration pipeline (nullable).
   * @param requestProvider The provider the request-level options name (nullable).
   * @param defaultProvider The fallback provider name.
   * @return The resolved provider string.
   * @throws CoreException If no provider can be resolved.
   */
  private String resolveProvider(
      PromptContext pipelineContext, String requestProvider, String defaultProvider) {
    String provider =
        Optional.ofNullable(pipelineContext).map(PromptContext::routedProvider).orElse(null);

    if (provider == null || provider.isBlank()) {
      provider = requestProvider;
    }
    if (provider == null || provider.isBlank()) {
      provider = defaultProvider;
    }
    if (provider == null || provider.isBlank()) {
      throw new CoreException("Missing required property: orazaka.core.default-provider");
    }
    return provider;
  }

  /**
   * Resolves the active Ollama chat model name when provider is Ollama.
   *
   * @param provider The resolved provider name.
   * @return The active Ollama model name, or null if provider is not Ollama.
   * @throws CoreException If provider is Ollama but no active model is registered.
   */
  private String resolveOllamaChatModel(String provider) {
    if (!ProviderClassifier.ollama().equalsIgnoreCase(provider)) {
      return null;
    }
    return modelCatalogProvider
        .getActiveChatModel()
        .orElseThrow(
            () ->
                new CoreException(
                    "Capability missing: No active Ollama chat model found in the model registry."));
  }
}
