package com.krizaka.sovkora.core.application.routing;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.ClassificationResponse;
import com.krizaka.sovkora.core.domain.model.ConditionalRoute;
import com.krizaka.sovkora.core.domain.ports.outbound.SemanticClassifierPort;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Semantic routing engine that classifies incoming prompts via LocalAI and resolves the optimal
 * Phase 2 interceptor set at runtime.
 *
 * <p>Processing flow:
 *
 * <ol>
 *   <li>Sends the raw prompt to LocalAI {@code POST /v1/classify} for intent classification
 *   <li>Matches classified intents against registered {@link ConditionalRoute} rules
 *   <li>Collects the union of required interceptor keys from all matched routes
 *   <li>Resolves matching interceptor beans from the Spring-managed registry
 * </ol>
 *
 * <p><b>Graceful Degradation</b>: If LocalAI is unreachable or returns an error, the engine logs a
 * warning and returns an empty list. The caller ({@code DynamicPipelineExecutor}) falls back to the
 * full deterministic chain.
 *
 * <p><b>Thread Safety</b>: This component is stateless — all state is passed via method parameters.
 * Safe for concurrent invocation from Virtual Threads.
 */
public final class SemanticRoutingEngine {

  private static final Logger logger = LoggerFactory.getLogger(SemanticRoutingEngine.class);

  private final SemanticClassifierPort classifierPort;

  /**
   * Constructs the semantic routing engine with the semantic classifier port.
   *
   * @param classifierPort The port used to classify the prompt intents.
   */
  public SemanticRoutingEngine(SemanticClassifierPort classifierPort) {
    this.classifierPort = classifierPort;
  }

  /**
   * Classifies a prompt via the classifier port and returns the list of matched intent labels.
   *
   * @param prompt The raw user prompt text to classify.
   * @return List of matched intent labels above their respective confidence thresholds, or an empty
   *     list if classification fails.
   */
  public ClassificationResponse classify(String prompt) {
    return classifierPort.classify(prompt);
  }

  /**
   * Evaluates the prompt semantically and resolves the matching Phase 2 interceptor beans from the
   * registry.
   *
   * <p>For each classified intent, checks all provided routes. If a route's intent label matches
   * and the confidence exceeds the threshold, the route's required interceptor keys are collected.
   * The final set is de-duplicated and resolved against the bean registry.
   *
   * @param prompt The raw user prompt to classify.
   * @param routes The conditional routing rules to evaluate against.
   * @param registry The interceptor bean registry keyed by class simple name.
   * @return Ordered list of Phase 2 interceptor beans to execute, or empty if no routes match.
   */
  public List<PromptContextInterceptor> resolveInterceptors(
      String prompt,
      List<ConditionalRoute> routes,
      Map<String, PromptContextInterceptor> registry) {

    ClassificationResponse classification = classify(prompt);
    if (classification.intents().isEmpty()) {
      return List.of();
    }

    Set<String> activatedKeys = new LinkedHashSet<>();
    for (ClassificationResponse.ClassifiedIntent intent : classification.intents()) {
      for (ConditionalRoute route : routes) {
        if (route.intentLabel().equals(intent.label()) && route.matches(intent.confidence())) {
          activatedKeys.addAll(route.requiredInterceptorKeys());
          logger.debug(
              "Route '{}' activated (confidence={}, threshold={}) — interceptors: {}",
              route.intentLabel(),
              intent.confidence(),
              route.confidenceThreshold(),
              route.requiredInterceptorKeys());
        }
      }
    }

    if (activatedKeys.isEmpty()) {
      logger.debug("No conditional routes matched — Phase 2 will use full deterministic chain.");
      return List.of();
    }

    List<PromptContextInterceptor> resolved = new ArrayList<>();
    for (String key : activatedKeys) {
      PromptContextInterceptor interceptor = registry.get(key);
      if (interceptor != null) {
        resolved.add(interceptor);
      } else {
        logger.warn(
            "Semantic route activated interceptor key '{}' but no registered bean found — skipping.",
            key);
      }
    }

    logger.info(
        "SemanticRoutingEngine resolved {} Phase 2 interceptor(s) from {} activated key(s).",
        resolved.size(),
        activatedKeys.size());
    return List.copyOf(resolved);
  }
}
