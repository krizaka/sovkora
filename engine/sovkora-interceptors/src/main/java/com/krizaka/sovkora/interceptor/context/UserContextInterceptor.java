package com.krizaka.sovkora.interceptor.context;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.pipeline.PipelineRegistry;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ContextInterceptor that dynamically injects onboarding profile details (ai_behavior,
 * primary_industry) as structural system prompt constraints right before executing model inference
 * or streaming.
 *
 * <p>Enable/disable is database-driven via {@code pipeline_interceptor_config} (resolved through
 * the {@link PipelineRegistry}), not yaml — replacing the former {@code
 * orazaka.core.orchestration.user-context.enabled} flag.
 */
class UserContextInterceptor implements PromptContextInterceptor {

  private static final Logger logger = LoggerFactory.getLogger(UserContextInterceptor.class);

  /** Registry key for this interceptor's enabled state (matches its simple class name). */
  private static final String INTERCEPTOR_KEY = "UserContextInterceptor";

  private final PipelineRegistry pipelineRegistry;

  UserContextInterceptor(PipelineRegistry pipelineRegistry) {
    this.pipelineRegistry =
        Objects.requireNonNull(pipelineRegistry, "PipelineRegistry must not be null");
  }

  @Override
  public EngineChatOptions preProcess(
      InternalChatRequest request,
      String promptText,
      List<EngineMessage> messages,
      EngineChatOptions options) {

    if (!pipelineRegistry.isInterceptorEnabled(INTERCEPTOR_KEY)) {
      return options;
    }

    try {
      Map<String, Object> preferences = request.context().preferences();
      String constraints = buildConstraints(preferences);
      if (!constraints.isEmpty()) {
        messages.addFirst(EngineMessage.system("System constraints:\n" + constraints));
        logger.debug("Successfully injected user profile system prompt constraints.");
      }
    } catch (RuntimeException e) {
      logger.error("Failed to inject user profile constraints in preProcess", e);
    }
    return options;
  }

  private String buildConstraints(Map<String, Object> preferences) {
    StringBuilder constraints = new StringBuilder();
    String primaryIndustry = resolvePreference(preferences, "primary_industry", "primaryIndustry");
    String aiBehavior = resolvePreference(preferences, "ai_behavior", "aiBehavior");

    if (primaryIndustry != null && !primaryIndustry.isBlank()) {
      constraints.append("User Primary Industry: ").append(primaryIndustry).append("\n");
    }
    if (aiBehavior != null && !aiBehavior.isBlank()) {
      constraints.append("User AI Behavior: ").append(aiBehavior).append("\n");
    }
    return constraints.toString();
  }

  /** Reads the user's own preference, under the namespace a Context gives them (ADR-064). */
  private String resolvePreference(Map<String, Object> prefs, String snakeKey, String camelKey) {
    String value = (String) prefs.get(Context.USER_PREFERENCE_NAMESPACE + snakeKey);
    return value != null ? value : (String) prefs.get(Context.USER_PREFERENCE_NAMESPACE + camelKey);
  }
}
