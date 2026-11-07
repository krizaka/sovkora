package com.krizaka.sovkora.interceptor.enrichment;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.jobs.domain.model.JobCommand;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Injects an installed Studio's brand kit into the prompt (ADR-034 §9.1).
 *
 * <p>Brand name, tone, signature and forbidden words reach every step of a Studio run without each
 * blueprint template repeating them. That matters because a Studio's value is a <b>consistent
 * voice</b> across five steps written by an admin months apart — repetition in templates is how
 * that consistency quietly drifts.
 *
 * <p>Reads the {@code orazaka.studio.brand.*} preference namespace, which the studio service stamps
 * into each job's payload. <b>Absent that namespace it is a no-op</b>: ordinary chat is unaffected,
 * which is why this can sit in the shared pipeline rather than a Studio-only one.
 *
 * <p>{@code isAiDependent()} stays {@code false}: this is pure enrichment with no model call, so it
 * survives the {@code orazaka.security.disable-ai} kill-switch — a Studio refused for lack of AI
 * should be refused at submission with a stated reason, not fail here mid-pipeline.
 */
class BrandContextInterceptor implements PromptContextInterceptor {

  /**
   * The key a producer declares its enrichment namespace under — never the namespace itself.
   *
   * <p>This was {@code BRAND_PREFIX = "orazaka.studio.brand."}: one namespace, the Studio
   * product's, compiled into an engine module. An external pack therefore could not have a context
   * at all without editing this file (ADR-049 §6). It now enriches whatever namespace the message
   * declares, so a pack's own prefix works with no engine change (ADR-050).
   */
  private static final String NAMESPACE_KEY = JobCommand.ENRICHMENT_NAMESPACE_KEY;

  @Override
  public EngineChatOptions preProcess(
      InternalChatRequest request,
      String promptText,
      List<EngineMessage> messages,
      EngineChatOptions options) {
    Map<String, String> brand = brandOf(request);
    if (brand.isEmpty()) {
      return options;
    }
    StringBuilder instruction = new StringBuilder("Brand context for this workspace:");
    brand.forEach(
        (key, value) -> instruction.append("\n- ").append(key).append(": ").append(value));
    messages.addFirst(EngineMessage.system(instruction.toString()));
    return options;
  }

  /**
   * Lifts the brand keys out of the request's context preferences.
   *
   * <p>Sorted so the rendered block is stable: an unordered map would change the prompt between
   * identical runs, which defeats caching and makes two runs of the same Studio incomparable.
   */
  private static Map<String, String> brandOf(InternalChatRequest request) {
    Context context = request == null ? null : request.context();
    if (context == null || context.preferences() == null) {
      return Map.of();
    }
    // No declaration, no enrichment. A turn that names no namespace is not "the Studio's" by
    // default — assuming a default is how the engine came to know one product's vocabulary.
    Object declared = context.preferences().get(NAMESPACE_KEY);
    if (!(declared instanceof String prefix) || prefix.isBlank()) {
      return Map.of();
    }
    Map<String, String> brand = new TreeMap<>();
    context
        .preferences()
        .forEach(
            (key, value) -> {
              if (key != null
                  && !NAMESPACE_KEY.equals(key)
                  && key.startsWith(prefix)
                  && value != null) {
                brand.put(key.substring(prefix.length()), String.valueOf(value));
              }
            });
    return brand;
  }
}
