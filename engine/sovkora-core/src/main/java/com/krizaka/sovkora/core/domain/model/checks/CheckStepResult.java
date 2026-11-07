package com.krizaka.sovkora.core.domain.model.checks;

import java.util.Map;
import java.util.Objects;

/**
 * What a blueprint step reads from an external check (sovkora-engine#84): the result, and the
 * consumption it reports for the settlement (sovkora-job-service#68, ADR-033 hold then settle).
 *
 * @param result the result of the check
 * @param calls the provider calls charged: 1 when the provider answered or accepted the search
 *     ({@code MATCH}, {@code NO_MATCH} or {@code PENDING}), 0 when the call was refused, failed or
 *     timed out
 */
public record CheckStepResult(CheckResult result, int calls) {

  /** Validates the step result. */
  public CheckStepResult {
    Objects.requireNonNull(result, "result must not be null");
    if (calls != 0 && calls != 1) {
      throw new IllegalArgumentException("a check is one call or none");
    }
  }

  /**
   * The step result of a check: charged exactly when the provider answered.
   *
   * @param result the result
   * @return the step result
   */
  public static CheckStepResult of(CheckResult result) {
    return new CheckStepResult(result, result.isCharged() ? 1 : 0);
  }

  /**
   * The step's consumption, as the settlement reads it.
   *
   * @return {@code {calls: 0|1}}
   */
  public Map<String, Integer> consumption() {
    return Map.of("calls", calls);
  }
}
