package com.krizaka.sovkora.automationservice.domain.model;

/**
 * The states of an automation run that a cloud automation reaches (a subset of the Postgres enum
 * {@code automation_run_status}, ADR-079 §1).
 */
public enum RunStatus {
  /** Recorded, the Studio run not yet started. */
  QUEUED,
  /** The Studio run was accepted and is going. */
  RUNNING,
  /** The Studio run succeeded. */
  SUCCEEDED,
  /** The run could not start, or the Studio run failed. */
  FAILED,
  /** The day's budget of runs was reached; nothing ran. */
  SKIPPED_BUDGET;

  /**
   * Whether the run is over.
   *
   * @return true when no further change is expected
   */
  public boolean isFinal() {
    return this == SUCCEEDED || this == FAILED || this == SKIPPED_BUDGET;
  }
}
