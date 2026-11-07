package com.krizaka.sovkora.core.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable metadata schema representing the compiled two-phase pipeline architecture.
 *
 * <p>Serialized to JSON and pushed as an Early-Ack SSE event before LLM inference begins, giving
 * the UI immediate visibility into the active pipeline composition and estimated overhead.
 *
 * @param pipelineId Unique identifier for the active pipeline configuration.
 * @param coreInterceptorIds Phase 1 immutable interceptor identifiers (always executed).
 * @param dynamicInterceptorIds Phase 2 semantically-routed interceptor identifiers.
 * @param estimatedLatencyMs Estimated pipeline overhead in milliseconds (sum of interceptor costs).
 */
public record AdvancedPipelineSchema(
    String pipelineId,
    List<String> coreInterceptorIds,
    List<String> dynamicInterceptorIds,
    long estimatedLatencyMs) {

  /** Compact constructor enforcing non-null invariants and defensive copies. */
  public AdvancedPipelineSchema {
    Objects.requireNonNull(pipelineId, "pipelineId must not be null");
    coreInterceptorIds = coreInterceptorIds != null ? List.copyOf(coreInterceptorIds) : List.of();
    dynamicInterceptorIds =
        dynamicInterceptorIds != null ? List.copyOf(dynamicInterceptorIds) : List.of();
  }

  /**
   * Returns the total count of active interceptors across both phases.
   *
   * @return Combined interceptor count.
   */
  public int totalInterceptorCount() {
    return coreInterceptorIds.size() + dynamicInterceptorIds.size();
  }
}
