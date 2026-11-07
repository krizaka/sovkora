package com.krizaka.sovkora.core.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Pure data record holding generated test assertions for Tier D (Test-Driven Response) validation.
 *
 * <p>Produced by the {@code TestShaperPort} outbound port and consumed by the {@code
 * ClosedLoopValidationInterceptor} to validate LLM responses against pre-generated assertion
 * schemas.
 *
 * @param schemaName A human-readable identifier for the assertion schema context.
 * @param assertions The list of assertion rules to evaluate against the LLM response.
 * @param generatedAt ISO-8601 timestamp of when the assertions were generated.
 * @since 1.1.0
 */
public record AssertionContract(String schemaName, List<String> assertions, String generatedAt) {

  /** Compact constructor enforcing non-null invariants and defensive copies. */
  public AssertionContract {
    Objects.requireNonNull(schemaName, "schemaName must not be null");
    assertions = assertions != null ? List.copyOf(assertions) : List.of();
    Objects.requireNonNull(generatedAt, "generatedAt must not be null");
  }

  /**
   * Returns an empty contract indicating no assertions were generated.
   *
   * @return An empty assertion contract.
   */
  public static AssertionContract empty() {
    return new AssertionContract("none", List.of(), "");
  }

  /**
   * Checks whether this contract contains any actionable assertions.
   *
   * @return {@code true} if the assertion list is non-empty.
   */
  public boolean hasAssertions() {
    return !assertions.isEmpty();
  }
}
