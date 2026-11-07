package com.krizaka.sovkora.core.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable pipeline configuration snapshot used by the {@code PipelineRegistry}.
 *
 * <p>Captures the full two-phase pipeline state: Phase 1 core interceptors (immutable governance),
 * Phase 2 dynamic interceptors (semantically routed), and the conditional routing rules that
 * determine Phase 2 composition at runtime.
 *
 * <p>Stored in the {@code AtomicReference<Map<String, PipelineConfig>>} inside {@code
 * PipelineRegistry} for zero-downtime hot-reload.
 *
 * @param pipelineId Unique identifier for this pipeline configuration.
 * @param coreInterceptors Phase 1 interceptors — mandatory, non-bypassable.
 * @param dynamicInterceptors Phase 2 interceptors — eligible for semantic routing selection.
 * @param routes Conditional routing rules for Phase 2 interceptor selection.
 */
public record PipelineConfig(
    String pipelineId,
    List<InterceptorConfig> coreInterceptors,
    List<InterceptorConfig> dynamicInterceptors,
    List<ConditionalRoute> routes) {

  /** Default pipeline identifier used when no explicit pipeline is specified. */
  public static final String DEFAULT_PIPELINE_ID = "default";

  /** Compact constructor enforcing non-null invariants and defensive copies. */
  public PipelineConfig {
    Objects.requireNonNull(pipelineId, "pipelineId must not be null");
    coreInterceptors = coreInterceptors != null ? List.copyOf(coreInterceptors) : List.of();
    dynamicInterceptors =
        dynamicInterceptors != null ? List.copyOf(dynamicInterceptors) : List.of();
    routes = routes != null ? List.copyOf(routes) : List.of();
  }

  /**
   * Returns all interceptor keys from the core phase.
   *
   * @return List of core interceptor bean keys.
   */
  public List<String> coreInterceptorKeys() {
    return coreInterceptors.stream().map(InterceptorConfig::interceptorKey).toList();
  }

  /**
   * Returns all interceptor keys from the dynamic phase.
   *
   * @return List of dynamic interceptor bean keys.
   */
  public List<String> dynamicInterceptorKeys() {
    return dynamicInterceptors.stream().map(InterceptorConfig::interceptorKey).toList();
  }
}
