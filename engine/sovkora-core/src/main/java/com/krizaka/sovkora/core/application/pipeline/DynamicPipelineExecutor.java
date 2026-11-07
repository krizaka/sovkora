package com.krizaka.sovkora.core.application.pipeline;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.application.routing.SemanticRoutingEngine;
import com.krizaka.sovkora.core.domain.event.PipelineStageEvent;
import com.krizaka.sovkora.core.domain.model.*;
import com.krizaka.sovkora.core.domain.ports.outbound.DomainEventPublisher;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.core.infrastructure.config.SecurityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Two-phase dynamic pipeline executor with semantic routing, Micrometer telemetry, and thread-safe
 * hot-reload support.
 *
 * <p>Execution flow:
 *
 * <ol>
 *   <li><b>Phase 1 — Core Immutable Pipeline</b>: Identity/Role Verification →
 *       Security/Anonymization → Base Context/RAG Slicing. These interceptors are mandatory for ALL
 *       requests. They cannot be bypassed, toggled, or reordered by any UI action.
 *   <li><b>Semantic Routing</b>: Between Phase 1 and Phase 2, the {@link SemanticRoutingEngine}
 *       evaluates the prompt and dynamically selects the required Phase 2 interceptors.
 *   <li><b>Phase 2 — Dynamic Custom Pipeline</b>: Only the semantically-matched interceptors
 *       execute. Falls back to the full deterministic chain if no routes match.
 * </ol>
 *
 * <p><b>Micrometer Telemetry</b>:
 *
 * <ul>
 *   <li>{@code orazaka.pipeline.routing.latency} — Timer tracking semantic classification
 *       round-trip.
 *   <li>{@code orazaka.pipeline.active.interceptors.count} — Gauge tracking live interceptor count
 *       per execution.
 * </ul>
 *
 * <p><b>Thread Safety</b>: All mutable state is confined to method-local variables. The {@link
 * PipelineRegistry} provides atomic snapshots. No {@code synchronized} blocks over I/O.
 *
 * @see PipelineRegistry
 * @see SemanticRoutingEngine
 */
public final class DynamicPipelineExecutor {

  private static final Logger logger = LoggerFactory.getLogger(DynamicPipelineExecutor.class);

  private final Map<String, PromptContextInterceptor> interceptorRegistry;
  private final List<PromptContextInterceptor> fallbackChain;
  private final PipelineRegistry pipelineRegistry;
  private final SemanticRoutingEngine routingEngine;
  private final SecurityProperties securityProperties;
  private final CoreProperties properties;
  private final Timer routingLatencyTimer;
  private final AtomicInteger activeInterceptorGauge;
  private final DomainEventPublisher eventPublisher;

  /**
   * Constructs the two-phase pipeline executor.
   *
   * @param interceptors Registered PromptContextInterceptor beans (auto-discovered via Spring Boot
   *     auto-configuration from interceptor submodules).
   * @param pipelineRegistry Thread-safe pipeline configuration registry.
   * @param routingEngine Semantic routing engine for Phase 2 interceptor selection.
   * @param securityProperties Security governance configuration.
   * @param properties Core configuration properties.
   * @param meterRegistry Micrometer meter registry for telemetry.
   * @param eventPublisher Publisher used to emit per-interceptor {@link PipelineStageEvent}s.
   */
  public DynamicPipelineExecutor(
      List<PromptContextInterceptor> interceptors,
      PipelineRegistry pipelineRegistry,
      SemanticRoutingEngine routingEngine,
      SecurityProperties securityProperties,
      CoreProperties properties,
      MeterRegistry meterRegistry,
      DomainEventPublisher eventPublisher) {

    this.eventPublisher = eventPublisher;
    List<PromptContextInterceptor> all =
        new ArrayList<>(interceptors != null ? interceptors : List.of());

    this.interceptorRegistry =
        all.stream()
            .collect(Collectors.toMap(i -> i.getClass().getSimpleName(), i -> i, (a, b) -> a));

    // Ordering is resolved dynamically by the PipelineRegistry from database configuration.
    // The fallbackChain preserves Spring injection order — no hardcoded sort.
    this.fallbackChain = List.copyOf(all);

    this.pipelineRegistry = pipelineRegistry;
    this.routingEngine = routingEngine;
    this.securityProperties =
        securityProperties != null ? securityProperties : new SecurityProperties(false);
    this.properties = properties;

    // Micrometer telemetry
    this.routingLatencyTimer =
        Timer.builder("orazaka.pipeline.routing.latency")
            .description("Semantic classification round-trip latency")
            .register(meterRegistry);

    this.activeInterceptorGauge = new AtomicInteger(0);
    meterRegistry.gauge("orazaka.pipeline.active.interceptors.count", activeInterceptorGauge);

    logger.info(
        "Initialized DynamicPipelineExecutor with {} registered interceptors. "
            + "Security kill-switch: {}",
        this.interceptorRegistry.size(),
        this.securityProperties.disableAi() ? "ACTIVE" : "inactive");
  }

  /**
   * Refuses the turn when the master orchestration switch is off — the single author of what off
   * means (ADR-062).
   *
   * <p><b>Off refuses; it never bypasses.</b> This switch used to return {@code null} here, and the
   * engine then called the model with the raw prompt: Phase 1 (the crisis guard, the scope guard)
   * and Phase 2 (the entitlement check and its credit hold) were skipped, and so was {@link
   * #enforceSecurityGate}, the only place {@code disable-ai} is enforced. An operator reaching for
   * a kill-switch would have got ungoverned, unmetered inference. It could not be set to {@code
   * false} until ADR-062 repaired its binding, which is the only reason that never happened.
   *
   * <p>Read here and nowhere else. ADR-062 also read it in the engine, ahead of a branch that
   * skipped this whole method for a prompt carrying media; that branch is gone (ADR-063), every
   * turn comes through {@code process}, and one read site is one author.
   *
   * @throws PipelineDisabledException when {@code orazaka.core.orchestration.enabled} is false
   */
  private void requireEnabled() {
    boolean enabled = properties.orchestration() == null || properties.orchestration().enabled();
    if (!enabled) {
      throw new PipelineDisabledException();
    }
  }

  /**
   * Executes the two-phase interceptor pipeline on the turn's text.
   *
   * @param rawUserQuery The turn's text, with any media already lifted out of it.
   * @param mediaParts How many non-text parts travel with the turn. Declared by the engine, which
   *     lifted them, so that no interceptor mistakes the text for the whole turn (ADR-063).
   * @param context The execution context.
   * @param pipelineId The pipeline configuration identifier (nullable — uses default).
   * @return The enriched {@link PromptContext}
   * @throws PipelineDisabledException if the master orchestration switch is off (ADR-062)
   * @throws SecurityException if an AI-dependent interceptor is invoked while the governance
   *     kill-switch is active.
   * @throws PipelineShortCircuitException if a gating interceptor refuses the request.
   * @throws RuntimeException the unhandled failure of an interceptor that declares {@link
   *     PromptContextInterceptor#failsClosed()}; any other interceptor's failure is logged and the
   *     turn continues with the context as it was (ADR-064).
   */
  public PromptContext process(
      String rawUserQuery, int mediaParts, Context context, String pipelineId) {
    requireEnabled();

    logger.debug("DynamicPipelineExecutor: starting two-phase execution...");

    Map<String, Object> userMetadata = new HashMap<>();
    userMetadata.put("userId", context.userId());
    userMetadata.put("conversationId", context.conversationId());
    userMetadata.putAll(namespaced(context.preferences()));
    userMetadata.put("roles", context.authorities().stream().map(Authority::name).toList());

    RoutingMode mode = resolveRoutingMode();
    PromptContext promptContext =
        new PromptContext(
            rawUserQuery, userMetadata, Map.of(), rawUserQuery, null, mode, mediaParts);

    // ── Phase 1: Core Immutable Pipeline ──
    List<PromptContextInterceptor> coreChain = resolveCoreChain(pipelineId);
    logger.debug("Phase 1: executing {} core interceptor(s).", coreChain.size());

    for (PromptContextInterceptor interceptor : coreChain) {
      enforceSecurityGate(interceptor);
      try {
        promptContext = interceptor.beforeExecution(promptContext);
      } catch (PipelineShortCircuitException refusal) {
        // A refusal, not a failure: the gate did its job and the request must stop here.
        logger.info(
            "Phase 1 interceptor '{}' short-circuited the request: {}",
            interceptor.getClass().getSimpleName(),
            refusal.reason());
        throw refusal;
      } catch (RuntimeException e) {
        failOrDegrade("Phase 1", interceptor, e);
      }
      emitStageCompleted(context, interceptor);
    }

    // ── Semantic Routing (between Phase 1 and Phase 2) ──
    List<PromptContextInterceptor> dynamicChain;
    if (mode == RoutingMode.SEMANTIC) {
      dynamicChain = resolveSemanticChain(rawUserQuery, pipelineId);
    } else {
      dynamicChain = resolveDeterministicDynamicChain(pipelineId);
    }

    // ── Phase 2: Dynamic Custom Pipeline ──
    int totalCount = coreChain.size() + dynamicChain.size();
    activeInterceptorGauge.set(totalCount);
    logger.debug("Phase 2: executing {} dynamic interceptor(s).", dynamicChain.size());

    for (PromptContextInterceptor interceptor : dynamicChain) {
      enforceSecurityGate(interceptor);
      try {
        promptContext = interceptor.beforeExecution(promptContext);
      } catch (PipelineShortCircuitException refusal) {
        // A refusal, not a failure: the gate did its job and the request must stop here.
        logger.info(
            "Phase 2 interceptor '{}' short-circuited the request: {}",
            interceptor.getClass().getSimpleName(),
            refusal.reason());
        throw refusal;
      } catch (RuntimeException e) {
        failOrDegrade("Phase 2", interceptor, e);
      }
      emitStageCompleted(context, interceptor);
      if (promptContext.cachedResponse().isPresent()) {
        // The turn is answered (not refused): nothing after the cache has anything left to do.
        logger.info(
            "Phase 2 interceptor '{}' answered the turn; the rest of the chain is skipped.",
            interceptor.getClass().getSimpleName());
        break;
      }
    }

    logger.debug(
        "DynamicPipelineExecutor completed: {} total interceptor(s) executed.", totalCount);
    return promptContext;
  }

  /**
   * Applies the failure posture the interceptor declared (ADR-064): a control's unhandled failure
   * stops the turn, an enrichment's degrades it.
   *
   * @throws RuntimeException the control's own failure, unchanged, when it declares {@link
   *     PromptContextInterceptor#failsClosed()}
   */
  private static void failOrDegrade(
      String phase, PromptContextInterceptor interceptor, RuntimeException failure) {
    String name = interceptor.getClass().getSimpleName();
    if (interceptor.failsClosed()) {
      logger.error("{} control '{}' failed — the turn stops rather than passes.", phase, name);
      throw failure;
    }
    logger.error(
        "{} interceptor '{}' failed — continuing with current context.", phase, name, failure);
  }

  /**
   * The caller's preferences, reduced to the two namespaces a {@link Context} may carry.
   *
   * <p>The same discipline {@code JobListener} applies to a job's payload, applied here to the
   * preferences, which never had it: the merge takes a namespaced map, not a raw one. The engine's
   * own keys ({@code userId}, {@code conversationId}, {@code roles}) carry no namespace, so no
   * entry that crosses can be one of them. Before ADR-064 the whole map crossed, a stored
   * preference named {@code userId} replaced the actor the credit hold was taken on, and {@code
   * roles} was safe only because its line happened to sit below the merge — an order nobody chose.
   * That order is left as it was: nothing depends on it any more, and a fourth engine key written
   * above the merge is as safe as the three.
   */
  private static Map<String, Object> namespaced(Map<String, Object> preferences) {
    return preferences.entrySet().stream()
        .filter(
            entry ->
                entry.getKey().startsWith(Context.PLATFORM_NAMESPACE)
                    || entry.getKey().startsWith(Context.USER_PREFERENCE_NAMESPACE))
        .collect(HashMap::new, (map, e) -> map.put(e.getKey(), e.getValue()), HashMap::putAll);
  }

  /**
   * Builds the Early-Ack pipeline schema for SSE metadata streaming.
   *
   * <p>Called by the SSE controller before LLM inference begins to push pipeline architecture
   * metadata to the UI.
   *
   * @param pipelineId The pipeline configuration identifier.
   * @param rawUserQuery The raw prompt (used for semantic routing preview).
   * @return The compiled {@link AdvancedPipelineSchema} ready for JSON serialization.
   */
  public AdvancedPipelineSchema buildSchema(String pipelineId, String rawUserQuery) {
    PipelineConfig config = pipelineRegistry.getConfig(pipelineId);
    List<String> coreIds = config.coreInterceptorKeys();

    RoutingMode mode = resolveRoutingMode();
    List<String> dynamicIds;
    if (mode == RoutingMode.SEMANTIC) {
      List<PromptContextInterceptor> semanticChain = resolveSemanticChain(rawUserQuery, pipelineId);
      dynamicIds = semanticChain.stream().map(i -> i.getClass().getSimpleName()).toList();
    } else {
      dynamicIds = config.dynamicInterceptorKeys();
    }

    long estimatedLatencyMs = (coreIds.size() + dynamicIds.size()) * 5L;
    return new AdvancedPipelineSchema(config.pipelineId(), coreIds, dynamicIds, estimatedLatencyMs);
  }

  /**
   * Resolves the Phase 1 core interceptor chain from the pipeline registry.
   *
   * <p>Core interceptors are resolved by key from the Spring-managed bean registry. Missing beans
   * are logged and skipped — the chain is best-effort but never empty in a healthy deployment.
   */
  private List<PromptContextInterceptor> resolveCoreChain(String pipelineId) {
    PipelineConfig config = pipelineRegistry.getConfig(pipelineId);
    List<PromptContextInterceptor> chain = new ArrayList<>();
    for (String key : config.coreInterceptorKeys()) {
      PromptContextInterceptor interceptor = interceptorRegistry.get(key);
      if (interceptor != null) {
        chain.add(interceptor);
      } else {
        logger.warn("Core interceptor '{}' has no registered bean — skipping.", key);
      }
    }
    return List.copyOf(chain);
  }

  /**
   * Resolves the Phase 2 dynamic chain via semantic routing.
   *
   * <p>Delegates to {@link SemanticRoutingEngine#resolveInterceptors} with Micrometer timing. Falls
   * back to the deterministic dynamic chain if the routing engine returns empty.
   */
  private List<PromptContextInterceptor> resolveSemanticChain(
      String rawUserQuery, String pipelineId) {
    PipelineConfig config = pipelineRegistry.getConfig(pipelineId);

    Timer.Sample sample = Timer.start();
    List<PromptContextInterceptor> semanticResult =
        routingEngine.resolveInterceptors(rawUserQuery, config.routes(), interceptorRegistry);
    sample.stop(routingLatencyTimer);

    if (semanticResult.isEmpty()) {
      logger.debug(
          "Semantic routing returned empty — falling back to deterministic dynamic chain.");
      return resolveDeterministicDynamicChain(pipelineId);
    }
    return semanticResult;
  }

  /**
   * Resolves the Phase 2 dynamic chain from the deterministic database configuration.
   *
   * <p>Filters to enabled interceptors only and resolves beans from the registry.
   */
  private List<PromptContextInterceptor> resolveDeterministicDynamicChain(String pipelineId) {
    PipelineConfig config = pipelineRegistry.getConfig(pipelineId);
    List<PromptContextInterceptor> chain = new ArrayList<>();
    for (var interceptorConfig : config.dynamicInterceptors()) {
      if (!interceptorConfig.enabled()) {
        continue;
      }
      PromptContextInterceptor interceptor =
          interceptorRegistry.get(interceptorConfig.interceptorKey());
      if (interceptor != null) {
        chain.add(interceptor);
      } else {
        logger.warn(
            "Dynamic interceptor '{}' has no registered bean — skipping.",
            interceptorConfig.interceptorKey());
      }
    }
    if (chain.isEmpty()) {
      logger.debug("No dynamic interceptors resolved from DB — using fallback chain minus core.");
      return fallbackChain.stream()
          .filter(i -> !PipelineRegistry.class.getName().contains(i.getClass().getSimpleName()))
          .toList();
    }
    return List.copyOf(chain);
  }

  /**
   * Publishes a {@link PipelineStageEvent} once an interceptor finishes, so the Router's SSE relay
   * can stream live progress. No-op when no conversation context or publisher is available (e.g.
   * the synchronous, non-streaming chat path or unit tests).
   *
   * @param context The execution context (provides the conversation id).
   * @param interceptor The interceptor that just completed.
   */
  private void emitStageCompleted(Context context, PromptContextInterceptor interceptor) {
    if (eventPublisher == null || context == null || context.conversationId() == null) {
      return;
    }
    eventPublisher.publish(
        new PipelineStageEvent(context.conversationId(), interceptor.getClass().getSimpleName()));
  }

  /**
   * Enforces the security governance kill-switch. Throws a hard {@link SecurityException} if the
   * interceptor is AI-dependent and the kill-switch is active.
   *
   * @param interceptor The interceptor to validate.
   * @throws SecurityException if the interceptor requires AI and the kill-switch is enabled.
   */
  private void enforceSecurityGate(PromptContextInterceptor interceptor) {
    if (securityProperties.disableAi() && interceptor.isAiDependent()) {
      String name = interceptor.getClass().getSimpleName();
      logger.error(
          "SECURITY GATE VIOLATION — AI-dependent interceptor '{}' blocked by governance "
              + "kill-switch (orazaka.security.disable-ai=true).",
          name);
      throw new SecurityException(
          "AI governance kill-switch is active. AI-dependent interceptor '"
              + name
              + "' is prohibited. Set orazaka.security.disable-ai=false to re-enable.");
    }
  }

  /**
   * Resolves the active routing mode from configuration.
   *
   * @return The configured routing mode, defaulting to DETERMINISTIC.
   */
  private RoutingMode resolveRoutingMode() {
    if (properties.orchestration() != null && properties.orchestration().routing() != null) {
      return properties.orchestration().routing().mode();
    }
    return RoutingMode.DETERMINISTIC;
  }

  /**
   * Evicts the pipeline registry cache, forcing a reload from the database on next access.
   *
   * <p>Called by admin controllers after configuration changes. Active SSE streams are unaffected.
   */
  public void evictAndReload() {
    pipelineRegistry.reload();
    logger.info("DynamicPipelineExecutor cache evicted — PipelineRegistry reloaded from database.");
  }

  /**
   * Convenience overload for callers that do not specify a pipeline ID.
   *
   * @param rawUserQuery The turn's text, with any media already lifted out of it.
   * @param mediaParts How many non-text parts travel with the turn.
   * @param context The execution context.
   * @return The enriched {@link PromptContext}
   * @throws PipelineDisabledException if the master orchestration switch is off (ADR-062)
   */
  public PromptContext process(String rawUserQuery, int mediaParts, Context context) {
    return process(rawUserQuery, mediaParts, context, null);
  }

  /**
   * Evicts the cached chain, forcing a rebuild from the database on next {@code process()} call.
   * Called by admin controllers after configuration changes.
   */
  public void evictChainCache() {
    pipelineRegistry.reload();
    logger.info("Pipeline chain cache evicted — next execution will rebuild from database.");
  }

  /**
   * Returns the current chain configuration as domain records.
   *
   * @return Ordered list of interceptor configs reflecting the current chain state.
   */
  public List<InterceptorConfig> getCurrentConfig() {
    PipelineConfig config = pipelineRegistry.getConfig(null);
    List<InterceptorConfig> all = new ArrayList<>(config.coreInterceptors());
    all.addAll(config.dynamicInterceptors());
    return List.copyOf(all);
  }

  /**
   * Builds default interceptor configurations from the hardcoded fallback chain.
   *
   * @return List of default interceptor configs.
   */
  public List<InterceptorConfig> buildDefaultConfigs() {
    return fallbackChain.stream()
        .map(
            i ->
                new InterceptorConfig(
                    i.getClass().getSimpleName(),
                    PipelineUtils.humanize(i.getClass().getSimpleName()),
                    0,
                    true,
                    ""))
        .toList();
  }
}
