package com.krizaka.sovkora.core.domain.ports.outbound;

/**
 * Mandatory caller-supplied bridge to the host's existing step persistence, not a new step store.
 */
@FunctionalInterface
public interface StepModelRecorder {

  /**
   * Records the selected model before each invocation, including a fallback attempt.
   *
   * <p>Any failure aborts execution without invoking the model or trying another candidate.
   *
   * @param stepId host-owned step identifier
   * @param modelName selected model name
   */
  void recordModel(String stepId, String modelName);
}
