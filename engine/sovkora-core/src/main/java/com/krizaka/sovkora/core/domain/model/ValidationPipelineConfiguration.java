package com.krizaka.sovkora.core.domain.model;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable domain record representing a single validation pipeline step configuration.
 *
 * <p>Persisted in the {@code validation_pipeline_configs} table and resolved at runtime by the
 * {@code ClosedLoopValidationInterceptor} to dynamically compose the 4-tier validation chain.
 *
 * @param id The unique identifier of this configuration entry.
 * @param stepType The validation tier type (A, B, C, or D).
 * @param enabled Whether this validation step is currently active.
 * @param executionOrder The execution position in the validation pipeline chain.
 * @param configurationPayload Step-specific configuration (timeouts, model names, etc.).
 * @since 1.1.0
 */
public record ValidationPipelineConfiguration(
    UUID id,
    ValidationStepType stepType,
    boolean enabled,
    int executionOrder,
    Map<String, Object> configurationPayload) {

  /** Compact constructor enforcing non-null invariants and defensive copies. */
  public ValidationPipelineConfiguration {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(stepType, "stepType must not be null");
    if (executionOrder < 0) {
      throw new IllegalArgumentException("executionOrder must be >= 0, got: " + executionOrder);
    }
    configurationPayload =
        configurationPayload != null ? Map.copyOf(configurationPayload) : Map.of();
  }
}
