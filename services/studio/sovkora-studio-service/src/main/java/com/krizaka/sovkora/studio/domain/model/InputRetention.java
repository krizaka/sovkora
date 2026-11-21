package com.krizaka.sovkora.studio.domain.model;

/**
 * What a run keeps of its inputs once it is over: the {@code inputs_mode} of a {@code
 * studio_run_retention_policy} row (krizaka-restricted#279).
 *
 * <p>The run itself is deleted by the retention window either way (ADR-034 §14, ADR-051); this
 * decides only whether the input fields that carry a file outlive the run's terminal state.
 */
public enum InputRetention {
  /** The inputs live as long as the run: the retention window deletes them with it. */
  KEEP,
  /**
   * The declared input fields are removed when the run reaches a terminal state; the run id, its
   * status, counters, fingerprint and findings stay.
   */
  PURGE_AT_TERMINAL
}
