package com.krizaka.sovkora.interceptor.reformulation;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

/**
 * Order 6 — Resolves fuzzy user questions against compiled context matrices and reformulates them
 * into clear, explicit instructions using an LLM call.
 *
 * <p>This interceptor is <strong>AI-dependent</strong> — it invokes an LLM model to refine the
 * prompt. The security governance kill-switch will block this interceptor when active.
 *
 * @since 1.0.0
 */
public class RefinerInterceptor implements PromptContextInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(RefinerInterceptor.class);

  private final ProviderChatClient chatClient;
  private final CoreProperties properties;
  private final Resource systemRefinementResource;
  private final Resource contextEnvelopeResource;

  public RefinerInterceptor(
      ProviderChatClient chatClient,
      CoreProperties properties,
      @Value("classpath:/prompts/system-refinement.st") Resource systemRefinementResource,
      @Value("classpath:/prompts/context-envelope.st") Resource contextEnvelopeResource) {
    this.chatClient = Objects.requireNonNull(chatClient, "ProviderChatClient must not be null");
    this.properties = Objects.requireNonNull(properties, "CoreProperties must not be null");
    this.systemRefinementResource =
        Objects.requireNonNull(
            systemRefinementResource, "systemRefinementResource must not be null");
    this.contextEnvelopeResource =
        Objects.requireNonNull(contextEnvelopeResource, "contextEnvelopeResource must not be null");
  }

  @Override
  public boolean isAiDependent() {
    return true;
  }

  @Override
  public PromptContext intercept(PromptContext context) {
    if (!isRefinerConfigured()) {
      return context;
    }
    if (context.carriesMedia()) {
      // The refinement envelope carries the text and the metadata — never the image. Refining "what
      // do you think?" without the photograph rewrites a question about content the refiner never
      // saw, and the rewrite, not the user's words, is what the vision model then receives beside
      // the image. Until ADR-063 media turns never reached this stage; they do now, and the user's
      // words are what they send, as before.
      logger.debug("Turn carries media the refiner cannot see; keeping the user's words.");
      return context;
    }

    String provider = resolveProvider();
    if (chatClient.resolve(provider).isEmpty()) {
      logger.warn("Refinement provider '{}' is not registered.", provider);
      return context;
    }

    return executeRefinement(provider, buildRefinementPrompt(context), context);
  }

  /**
   * Guards against missing refiner model wiring. Per-interceptor enable/disable is enforced
   * upstream by the {@code DynamicPipelineExecutor} from {@code pipeline_interceptor_config} — this
   * interceptor only runs when the executor has already deemed it enabled.
   */
  private boolean isRefinerConfigured() {
    return properties.orchestration() != null && properties.orchestration().refiner() != null;
  }

  private String resolveProvider() {
    return Optional.ofNullable(properties.orchestration())
        .map(CoreProperties.OrchestrationConfig::refiner)
        .map(CoreProperties.InterceptorConfig::provider)
        .orElse(properties.defaultProvider());
  }

  private List<EngineMessage> buildRefinementPrompt(PromptContext context) {
    return List.of(
        EngineMessage.system(PromptTemplates.read(systemRefinementResource)),
        EngineMessage.user(
            PromptTemplates.render(
                contextEnvelopeResource,
                Map.of(
                    "userMetadata", context.userMetadata().toString(),
                    "systemMetadata", context.systemMetadata().toString(),
                    "rawQuery", context.rawUserQuery()))));
  }

  private PromptContext executeRefinement(
      String provider, List<EngineMessage> messages, PromptContext context) {
    try {
      var opt = properties.orchestration().refiner();
      String registered = chatClient.resolve(provider).orElseThrow();
      return Optional.ofNullable(
              chatClient.complete(registered, provider, messages, opt.model(), opt.temperature()))
          .map(String::strip)
          .filter(refined -> !refined.isBlank())
          .map(
              refined -> {
                logger.debug("Refinement generated ({} chars)", refined.length());
                return context.withRefinedPrompt(refined);
              })
          .orElse(context);
    } catch (RuntimeException e) {
      logger.error("Failed to refine prompt.", e);
      return context;
    }
  }
}
