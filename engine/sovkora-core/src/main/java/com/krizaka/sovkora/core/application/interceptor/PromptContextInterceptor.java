package com.krizaka.sovkora.core.application.interceptor;

import com.krizaka.sovkora.core.application.pipeline.ScreenedResponse;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import java.util.List;

/**
 * Core port interface for the prompt and context orchestration pipeline. Unifies PromptInterceptor
 * and ContextInterceptor capabilities.
 *
 * <p>Implementations are loaded dynamically via Spring Boot auto-configuration from external
 * interceptor submodules ({@code sovkora-interceptor-*}).
 *
 * <p>Interceptors that require AI model calls must override {@link #isAiDependent()} to return
 * {@code true}. When the governance kill-switch ({@code orazaka.security.disable-ai=true}) is
 * active, the {@code DynamicPipelineExecutor} will throw a hard {@link SecurityException} if any
 * AI-dependent interceptor is invoked.
 *
 * <p><strong>Lifecycle contract</strong>: The pipeline executor invokes interceptors using the
 * two-phase lifecycle:
 *
 * <ol>
 *   <li>{@link #beforeExecution(PromptContext)} — Pre-inference enrichment (context building,
 *       metadata injection). Defaults to {@link #intercept(PromptContext)} for backward
 *       compatibility.
 *   <li>{@link #afterExecution(PromptContext, String)} — Post-inference processing (validation,
 *       logging, metrics). Defaults to no-op.
 * </ol>
 *
 * <p><strong>Ordering</strong>: Interceptors have zero compiled-in knowledge of their execution
 * position. Sequence is resolved dynamically by the {@code DynamicPipelineExecutor} from the
 * database-driven {@code PipelineRegistry}. Hardcoded ordering methods are prohibited.
 */
public interface PromptContextInterceptor {

  /**
   * Returns a stable identifier for this interceptor instance. Used by the pipeline registry for
   * bean resolution and logging.
   *
   * @return The interceptor identifier, defaulting to the simple class name.
   */
  default String getId() {
    return getClass().getSimpleName();
  }

  /**
   * Intercepts and enriches the PromptContext.
   *
   * @param context The current prompt context state.
   * @return The updated prompt context state.
   * @deprecated Use {@link #beforeExecution(PromptContext)} instead. Retained for backward
   *     compatibility — the default {@code beforeExecution} delegates to this method.
   */
  @Deprecated(since = "2026.2.0", forRemoval = false)
  default PromptContext intercept(PromptContext context) {
    return context;
  }

  /**
   * Pre-inference lifecycle hook. Invoked before model execution to enrich, transform, or gate the
   * prompt context.
   *
   * <p>Default implementation delegates to {@link #intercept(PromptContext)} for backward
   * compatibility with existing interceptor implementations.
   *
   * @param context The current prompt context state.
   * @return The updated prompt context state.
   */
  @SuppressWarnings("deprecation")
  default PromptContext beforeExecution(PromptContext context) {
    return intercept(context);
  }

  /**
   * Post-inference lifecycle hook. Invoked after model execution completes to perform validation,
   * logging, metrics collection, or response transformation.
   *
   * @param context The prompt context that was sent to the model.
   * @param responseText The text response returned by the model (may be null if inference failed).
   */
  default void afterExecution(PromptContext context, String responseText) {
    // Default no-op — override for post-inference processing
  }

  /**
   * Output screen. Runs on <b>every</b> answer the engine is about to serve, a fresh model answer
   * and a cached one alike, before it leaves: a control that reads answers implements it. It may
   * return the text to serve instead (a reviewed text, a redaction) or refuse with a {@link
   * com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException}.
   *
   * <p>Distinct from {@link #afterExecution}/{@link #postProcess}, which observe an answer already
   * served and cannot stop it.
   *
   * @param context the prompt context of the turn
   * @param responseText the answer about to be served
   * @return the text to serve (the argument when unchanged)
   */
  default String screenResponse(PromptContext context, String responseText) {
    return responseText;
  }

  /**
   * Output screen that can also annotate the response (a verdict, a badge). Defaults to {@link
   * #screenResponse} with no notes; a control that reports a verdict overrides this and lets {@code
   * screenResponse} delegate to it.
   *
   * @param context the prompt context of the turn
   * @param responseText the answer about to be served
   * @return the text to serve and the notes the response carries
   */
  default ScreenedResponse screen(PromptContext context, String responseText) {
    return ScreenedResponse.plain(screenResponse(context, responseText));
  }

  /**
   * Streaming screen, deterministic and cheap, called for every chunk with the text assembled so
   * far <b>before the chunk is emitted</b>. A control that can already tell the answer is wrong
   * refuses with a {@link
   * com.krizaka.sovkora.core.application.pipeline.PipelineShortCircuitException}, so the stream
   * stops instead of finishing (sovkora-engine#125). Does nothing by default.
   *
   * @param context the prompt context of the turn
   * @param textSoFar everything streamed so far, the chunk being screened included
   */
  default void screenChunk(PromptContext context, String textSoFar) {}

  /**
   * Final screen of a streamed answer, run on the assembled text before the final event. The chunks
   * have already left, so it can only accept (with notes) or refuse: it never rewrites. Does
   * nothing by default.
   *
   * @param context the prompt context of the turn
   * @param assembled the whole streamed answer
   * @return the notes the final event carries
   */
  default ScreenedResponse screenStreamed(PromptContext context, String assembled) {
    return ScreenedResponse.plain(assembled);
  }

  /**
   * Called once a <b>fresh</b> model answer has passed every output screen and is being served: the
   * moment a cache may keep it. Never called for a refused answer, nor for an answer that was
   * itself served from a cache. A failure here never loses the turn.
   *
   * @param context the prompt context of the turn
   * @param servedText the answer as served (after the screens)
   */
  default void responseServed(PromptContext context, String servedText) {}

  /**
   * Indicates whether this interceptor depends on AI model inference calls. Interceptors returning
   * {@code true} will be blocked by the security governance kill-switch when {@code
   * orazaka.security.disable-ai=true} is set.
   *
   * @return {@code true} if this interceptor requires AI model calls, {@code false} otherwise.
   */
  default boolean isAiDependent() {
    return false;
  }

  /**
   * Whether a failure this interceptor did not handle stops the turn (ADR-064).
   *
   * <p><b>Declared, not left to a {@code catch} block.</b> The pipeline used to swallow every
   * exception but a deliberate refusal, which is right for an enrichment — a broken RAG lookup
   * should degrade the answer, not lose the user's turn — and made every control fail open by
   * accident: a crisis guard that threw on a malformed declaration passed the turn, and a billing
   * gate that could not reach billing served it free, and nobody had chosen either. A control
   * declares {@code true}, and handles inside itself the failures it has decided to survive;
   * whatever it did not anticipate then stops the turn instead of passing it.
   *
   * @return {@code true} for a control, whose unhandled failure must stop the turn; {@code false}
   *     (the default) for an enrichment, whose failure degrades it
   */
  default boolean failsClosed() {
    return false;
  }

  /**
   * Enriches request messages or options before sending to the model.
   *
   * @param request The original Sovkora chat request.
   * @param promptText The current (possibly refined) prompt text.
   * @param messages The accumulator list of mapped messages.
   * @param options The current chat options.
   * @return The updated options (must not return null; should return the passed options if
   *     unchanged).
   */
  default EngineChatOptions preProcess(
      InternalChatRequest request,
      String promptText,
      List<EngineMessage> messages,
      EngineChatOptions options) {
    return options;
  }

  /**
   * Invoked after the model execution completes successfully.
   *
   * @param request The original Sovkora chat request.
   * @param promptText The (possibly refined) prompt text that was sent.
   * @param responseText The text response returned by the model.
   */
  default void postProcess(InternalChatRequest request, String promptText, String responseText) {}
}
