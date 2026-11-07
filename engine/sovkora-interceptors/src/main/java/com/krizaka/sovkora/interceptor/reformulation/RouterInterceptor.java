package com.krizaka.sovkora.interceptor.reformulation;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

/**
 * Order 7 — Analyzes user intent and selects the optimal AI provider using an LLM classification
 * call with temperature 0.0 for determinism.
 *
 * <p>This interceptor is <strong>AI-dependent</strong> — it invokes an LLM model to determine
 * routing. The security governance kill-switch will block this interceptor when active.
 *
 * @since 1.0.0
 */
public class RouterInterceptor implements PromptContextInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(RouterInterceptor.class);

  private final ProviderChatClient chatClient;
  private final CoreProperties properties;
  private final Resource routerSystemResource;

  public RouterInterceptor(
      ProviderChatClient chatClient,
      CoreProperties properties,
      @Value("classpath:/prompts/system-router.st") Resource routerSystemResource) {
    this.chatClient = Objects.requireNonNull(chatClient, "ProviderChatClient must not be null");
    this.properties = Objects.requireNonNull(properties, "CoreProperties must not be null");
    this.routerSystemResource =
        Objects.requireNonNull(routerSystemResource, "routerSystemResource must not be null");
  }

  @Override
  public boolean isAiDependent() {
    return true;
  }

  @Override
  public PromptContext intercept(PromptContext context) {
    if (!isRouterConfigured()) {
      return context;
    }
    if (context.carriesMedia()) {
      // The router chooses from the text alone and cannot tell which provider can see an image —
      // and a routed provider outranks the caller's own. A vision job that names its vision model
      // would be sent wherever "Analyze this image" reads best. Unrouted, the turn keeps the
      // provider its caller chose, as it did while the pipeline skipped media turns (ADR-063).
      logger.debug("Turn carries media the router cannot see; leaving the provider to the caller.");
      return context;
    }

    String provider = resolveProvider();
    if (chatClient.resolve(provider).isEmpty()) {
      logger.warn("Router provider '{}' is not registered.", provider);
      return context;
    }

    return executeRouting(provider, buildRouterPrompt(context), context);
  }

  /**
   * Guards against missing router model wiring. Per-interceptor enable/disable is enforced upstream
   * by the {@code DynamicPipelineExecutor} from {@code pipeline_interceptor_config} — this
   * interceptor only runs when the executor has already deemed it enabled.
   */
  private boolean isRouterConfigured() {
    return properties.orchestration() != null && properties.orchestration().router() != null;
  }

  private String resolveProvider() {
    return Optional.ofNullable(properties.orchestration())
        .map(CoreProperties.OrchestrationConfig::router)
        .map(CoreProperties.InterceptorConfig::provider)
        .orElse(properties.defaultProvider());
  }

  private List<EngineMessage> buildRouterPrompt(PromptContext context) {
    String availableModels = String.join(", ", chatClient.providers().stream().sorted().toList());
    // User message is inlined (no router-user.st core resource — the core prompt
    // allow-list only permits system-router.st / system-refinement.st / context-envelope.st).
    return List.of(
        EngineMessage.system(PromptTemplates.read(routerSystemResource)),
        EngineMessage.user(
            "Refined query:\n"
                + context.refinedPrompt()
                + "\n\nLocally available model keys: "
                + availableModels
                + "\n\nReturn only the optimal provider key."));
  }

  private PromptContext executeRouting(
      String provider, List<EngineMessage> messages, PromptContext context) {
    try {
      var opt = properties.orchestration().router();
      String registered = chatClient.resolve(provider).orElseThrow();
      return Optional.ofNullable(
              chatClient.complete(registered, provider, messages, opt.model(), 0.0))
          .map(String::strip)
          .filter(text -> !text.isBlank())
          .filter(chatClient.providers()::contains)
          .map(
              routedModel -> {
                logger.debug("Routed to: {}", routedModel);
                return context.withRoutedProvider(routedModel);
              })
          .orElse(context);
    } catch (RuntimeException e) {
      logger.error("Failed to route prompt.", e);
      return context;
    }
  }
}
