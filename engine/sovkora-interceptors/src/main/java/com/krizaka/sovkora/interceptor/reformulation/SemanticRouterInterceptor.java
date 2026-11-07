package com.krizaka.sovkora.interceptor.reformulation;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Order 7 — Evaluates multi-intent requests at {@code temperature: 0.0} to map out structured
 * execution plans as SIM-DAG (Structured Intent Map — Directed Acyclic Graph) dependency graphs for
 * complex sequential or parallel multimedia task decomposition.
 *
 * <p>Current implementation is a structural stub that identifies multi-intent patterns and logs the
 * decomposition. Full SIM-DAG graph execution will be wired via tracked ADR when the Jarvis
 * multi-media orchestration layer is production-ready.
 */
class SemanticRouterInterceptor implements PromptContextInterceptor {

  private static final Logger logger = LoggerFactory.getLogger(SemanticRouterInterceptor.class);

  private static final String DAG_STEPS_KEY = "semanticRouterSteps";
  private static final String INTENT_KEY = "resolvedIntent";

  @Override
  public PromptContext intercept(PromptContext context) {
    String prompt = context.refinedPrompt();
    if (prompt == null || prompt.isBlank()) {
      logger.debug("[SemanticRouterInterceptor] Empty refined prompt — defaulting to CHAT intent.");
      return injectIntent(context, "CHAT", List.of());
    }

    String detectedIntent = classifyIntent(prompt);
    List<String> dagSteps = decomposeDagSteps(prompt, detectedIntent);

    if (logger.isDebugEnabled()) {
      // Neither the prompt nor what was computed from it: [LOG-001] cannot tell an intent label
      // from a paraphrase, since both are text a method derived from the user's (ADR-064).
      logger.debug("[SemanticRouterInterceptor] Classified into {} DAG step(s).", dagSteps.size());
    }

    return injectIntent(context, detectedIntent, dagSteps);
  }

  /**
   * Classifies the primary intent of the refined prompt using keyword heuristics. Full
   * classification will use a dedicated LLM call at temperature 0.0 via tracked ADR.
   *
   * @param prompt The refined prompt text.
   * @return The classified intent category.
   */
  private String classifyIntent(String prompt) {
    String lower = prompt.toLowerCase();
    // Bilingual (EN + FR-CA) keyword heuristics. Video is checked before image so a
    // "generate a video of …" prompt is not mis-tagged IMAGE. Keywords are distinctive
    // multi-token phrases where possible to limit false positives on plain chat.
    if (containsAny(
        lower,
        "video",
        "vidéo",
        "animate",
        "animation",
        "create video",
        "generate video",
        "génère une vidéo",
        "crée une vidéo",
        "fais une vidéo")) {
      return "VIDEO";
    }
    if (containsAny(
        lower,
        "generate image",
        "create image",
        "image of",
        "an image",
        "draw",
        "illustration",
        "paint",
        "picture",
        "génère une image",
        "crée une image",
        "dessine",
        "une image de",
        "photo de")) {
      return "IMAGE";
    }
    if (containsAny(
        lower,
        "read aloud",
        "text to speech",
        "speak",
        "narrate",
        "voiceover",
        "tts",
        "à voix haute",
        "synthèse vocale",
        "voix off",
        "lis le texte",
        "prononce")) {
      return "SPEECH";
    }
    if (containsAny(
        lower,
        "run code",
        "execute",
        "python",
        "calculate",
        "compute",
        "exécute le code",
        "calcule")) {
      return "CODE_SANDBOX";
    }
    return "CHAT";
  }

  /**
   * Decomposes multi-intent prompts into ordered SIM-DAG sub-tasks. Current implementation produces
   * a single-node DAG. Full decomposition logic will be wired via tracked ADR.
   *
   * @param prompt The refined prompt text.
   * @param intent The primary classified intent.
   * @return An ordered list of DAG step descriptions.
   */
  private List<String> decomposeDagSteps(String prompt, String intent) {
    // Stub: single-step DAG. Multi-step decomposition requires LLM-assisted parsing.
    return List.of(intent + ": " + truncate(prompt, 200));
  }

  /**
   * Injects the resolved intent and DAG steps into system metadata.
   *
   * @param context The current prompt context.
   * @param intent The classified intent category.
   * @param dagSteps The ordered list of DAG step descriptions.
   * @return The enriched prompt context.
   */
  private PromptContext injectIntent(PromptContext context, String intent, List<String> dagSteps) {
    var enrichedSystem = new HashMap<>(context.systemMetadata());
    enrichedSystem.put(INTENT_KEY, intent);
    enrichedSystem.put(DAG_STEPS_KEY, List.copyOf(dagSteps));
    return context.withSystemMetadata(Map.copyOf(enrichedSystem));
  }

  /**
   * Checks if a string contains any of the specified keywords.
   *
   * @param text The text to search.
   * @param keywords The keywords to match.
   * @return {@code true} if any keyword is found.
   */
  private boolean containsAny(String text, String... keywords) {
    for (String keyword : keywords) {
      if (text.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Truncates a string to a maximum length for safe log output.
   *
   * @param text The text to truncate.
   * @param maxLen Maximum character length.
   * @return The truncated text.
   */
  private String truncate(String text, int maxLen) {
    if (text == null) {
      return "";
    }
    return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
  }
}
