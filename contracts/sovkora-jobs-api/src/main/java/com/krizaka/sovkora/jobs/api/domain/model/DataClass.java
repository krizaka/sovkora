package com.krizaka.sovkora.jobs.api.domain.model;

import java.time.Duration;
import java.util.Optional;

/**
 * What a piece of work's material is, for the obligations that follow from it (ADR-051, ADR-065).
 *
 * <p>Stamped on a Studio run from its pack's regulatory class and never re-derived: a regulatory
 * class is a property of a <b>pack</b>, which somebody can re-classify or withdraw, while a data
 * class is a property of <b>material that already exists</b>.
 *
 * <p><b>Why it lives in the jobs contract.</b> It used to be the studio contract's, and stopped at
 * the studio's edge: a SENSITIVE run's steps ran as jobs that carried no class, so the job plane
 * kept their payloads, outputs and refusals with no window at all while the run's own row aged out
 * (audit #27). The class now travels with the work, on {@link JobCommand}, like {@link
 * FailureCause} travels back; each context purges what it owns by the same vocabulary.
 */
public enum DataClass {

  /** Ordinary material: ordinary analytics, ordinary retention. */
  STANDARD,

  /** Personal but not health. Never exported, never trained on, shortened retention, audited. */
  SENSITIVE,

  /** Health or equivalent. Everything SENSITIVE carries, plus consent and the crisis guard. */
  REGULATED;

  /**
   * @return whether this class switches on the SENSITIVE controls
   */
  public boolean isProtected() {
    return this != STANDARD;
  }

  /**
   * How long the job plane keeps a job of this class after it reaches a terminal state.
   *
   * <p><b>The class parameterises the window; the window is not a separate mechanism.</b> A
   * protected job is needed while it runs — retries, a dead letter, diagnosis — and not one moment
   * after: its outcome has already been handed to the run, which keeps it under its own, class
   * driven window. So SENSITIVE and REGULATED keep nothing past terminal. STANDARD returns empty:
   * the job plane keeps an ordinary job as it always has, which is a retention question of its own
   * and not this one.
   *
   * @return the time after the terminal transition a job is kept, or empty when this window does
   *     not purge it
   */
  public Optional<Duration> jobRetentionAfterTerminal() {
    return isProtected() ? Optional.of(Duration.ZERO) : Optional.empty();
  }
}
