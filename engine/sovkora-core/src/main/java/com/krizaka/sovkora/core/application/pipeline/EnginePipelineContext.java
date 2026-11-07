package com.krizaka.sovkora.core.application.pipeline;

import com.krizaka.sovkora.core.domain.model.CachedResponse;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.chat.PreparedPrompt;
import java.util.List;

/**
 * Immutable compiled context snapshot produced by the {@link EnginePipelineBridge}.
 *
 * <p>Captures the results of prompt refinement, provider routing, conversation tracking, and the
 * final assembled {@link PreparedPrompt} ready for model invocation.
 *
 * @param provider The resolved AI provider name (e.g., {@code "ollama"}).
 * @param conversationId The conversation thread ID extracted from the request context.
 * @param promptText The refined or original prompt text string.
 * @param toPrompt The fully assembled {@link PreparedPrompt} with messages and options.
 * @param closeables The list of closeable client connections that must be cleaned up after
 *     execution.
 * @param holdId The credit reservation the entitlement gate took for this turn, or {@code null}
 *     when it was not metered. Carried through compilation because the gate runs in the pipeline
 *     while settlement happens after inference — this is what joins the two halves (ADR-033 §6.2).
 * @param cachedResponse The answer a cache found before inference, or {@code null}: the engine then
 *     serves it without calling the model (the output screens still run).
 * @param promptContext The pipeline's final context (what the output screens read), or {@code null}
 *     when no pipeline ran.
 */
public record EnginePipelineContext(
    String provider,
    String conversationId,
    String promptText,
    PreparedPrompt toPrompt,
    List<AutoCloseable> closeables,
    String holdId,
    CachedResponse cachedResponse,
    PromptContext promptContext) {

  public EnginePipelineContext(
      String provider,
      String conversationId,
      String promptText,
      PreparedPrompt toPrompt,
      List<AutoCloseable> closeables,
      String holdId) {
    this(provider, conversationId, promptText, toPrompt, closeables, holdId, null, null);
  }

  public EnginePipelineContext(
      String provider, String conversationId, String promptText, PreparedPrompt toPrompt) {
    this(provider, conversationId, promptText, toPrompt, List.of(), null);
  }

  public EnginePipelineContext(
      String provider,
      String conversationId,
      String promptText,
      PreparedPrompt toPrompt,
      List<AutoCloseable> closeables) {
    this(provider, conversationId, promptText, toPrompt, closeables, null);
  }

  public EnginePipelineContext {
    closeables = closeables == null ? List.of() : List.copyOf(closeables);
  }
}
