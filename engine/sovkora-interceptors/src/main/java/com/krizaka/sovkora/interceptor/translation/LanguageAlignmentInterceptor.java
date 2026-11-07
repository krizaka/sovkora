package com.krizaka.sovkora.interceptor.translation;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Order 3 — Forces the LLM backbone to process complex reasoning and tool chains in English to
 * maximize algorithmic and semantic token precision, while structurally enforcing final output
 * rendering in the user's native language.
 *
 * <p>Inspects {@link PromptContext} user metadata for a {@code locale} or {@code language} key. If
 * present, injects a system-level directive constraining reasoning language (English) and output
 * language (user's native tongue).
 *
 * @since 2026.1.0
 */
public class LanguageAlignmentInterceptor implements PromptContextInterceptor {

  private static final Logger logger = LoggerFactory.getLogger(LanguageAlignmentInterceptor.class);

  private static final String LANGUAGE_ALIGNMENT_KEY = "languageAlignmentDirective";

  /** The user's own locale and language, under the namespace a Context gives them (ADR-064). */
  private static final String PREFERENCE_LOCALE = Context.USER_PREFERENCE_NAMESPACE + "locale";

  private static final String PREFERENCE_LANGUAGE = Context.USER_PREFERENCE_NAMESPACE + "language";
  private static final String DEFAULT_REASONING_LANGUAGE = "English";

  @Override
  public PromptContext intercept(PromptContext context) {
    String userLanguage = resolveUserLanguage(context);

    if (DEFAULT_REASONING_LANGUAGE.equalsIgnoreCase(userLanguage)) {
      logger.debug(
          "[LanguageAlignmentInterceptor] User language is English — no alignment needed.");
      return context;
    }

    String directive = buildAlignmentDirective(DEFAULT_REASONING_LANGUAGE, userLanguage);

    var enrichedSystem = new HashMap<>(context.systemMetadata());
    enrichedSystem.put(LANGUAGE_ALIGNMENT_KEY, directive);

    logger.debug(
        "[LanguageAlignmentInterceptor] Injected language alignment — reasoning: {}, output: {}.",
        DEFAULT_REASONING_LANGUAGE,
        userLanguage);

    return context.withSystemMetadata(Map.copyOf(enrichedSystem));
  }

  /**
   * Resolves the user's native language from metadata. Falls back to English if undetectable.
   *
   * @param context The current prompt context.
   * @return The resolved user language string.
   */
  private String resolveUserLanguage(PromptContext context) {
    return Optional.ofNullable(context.userMetadata().get(PREFERENCE_LOCALE))
        .or(() -> Optional.ofNullable(context.userMetadata().get(PREFERENCE_LANGUAGE)))
        .map(Object::toString)
        .map(this::normalizeLanguageTag)
        .orElse(DEFAULT_REASONING_LANGUAGE);
  }

  /**
   * Normalizes raw locale tags (e.g., "fr_FR", "fr-FR", "fr") into display-ready language names.
   *
   * @param tag The raw locale tag.
   * @return The normalized language name.
   */
  private String normalizeLanguageTag(String tag) {
    if (tag == null || tag.isBlank()) {
      return DEFAULT_REASONING_LANGUAGE;
    }
    String base = tag.split("[_\\-]")[0].toLowerCase();
    return switch (base) {
      case "fr" -> "French";
      case "es" -> "Spanish";
      case "de" -> "German";
      case "pt" -> "Portuguese";
      case "it" -> "Italian";
      case "nl" -> "Dutch";
      case "ja" -> "Japanese";
      case "ko" -> "Korean";
      case "zh" -> "Chinese";
      case "ar" -> "Arabic";
      case "ru" -> "Russian";
      case "en" -> DEFAULT_REASONING_LANGUAGE;
      default -> tag;
    };
  }

  /**
   * Builds the structured alignment directive injected into system metadata.
   *
   * @param reasoningLanguage The language for internal reasoning (always English).
   * @param outputLanguage The user's native language for final output rendering.
   * @return The formatted directive string.
   */
  private String buildAlignmentDirective(String reasoningLanguage, String outputLanguage) {
    return "LANGUAGE_ALIGNMENT_CONSTRAINT: "
        + "Process all internal reasoning, tool invocations, and chain-of-thought steps in "
        + reasoningLanguage
        + ". Render the final user-facing response exclusively in "
        + outputLanguage
        + ". Do not mix languages in the output.";
  }
}
