package com.krizaka.sovkora.core.domain.model;

import java.util.Objects;

/**
 * Immutable domain record representing a pipeline interceptor's execution configuration.
 *
 * <p>Stored in PostgreSQL and resolved at runtime by {@link
 * com.krizaka.sovkora.core.application.pipeline.DynamicPipelineExecutor} to dynamically order the
 * interceptor chain.
 *
 * @param interceptorKey The bean class simple name (unique identifier).
 * @param displayLabel Human-readable label for admin UI.
 * @param executionOrder The execution position in the pipeline chain.
 * @param enabled Whether this interceptor is active.
 * @param description Optional description of the interceptor's responsibility.
 */
public record InterceptorConfig(
    String interceptorKey,
    String displayLabel,
    int executionOrder,
    boolean enabled,
    String description) {

  /** Compact constructor enforcing non-null invariants. */
  public InterceptorConfig {
    Objects.requireNonNull(interceptorKey, "interceptorKey must not be null");
    Objects.requireNonNull(displayLabel, "displayLabel must not be null");
  }
}
