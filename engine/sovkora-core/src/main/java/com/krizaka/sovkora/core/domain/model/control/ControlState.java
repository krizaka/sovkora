package com.krizaka.sovkora.core.domain.model.control;

/**
 * Where an answer stands with the control loop, as the UI reads it from the response metadata (key
 * {@link ControlMetadata#VERDICT}): a streamed chunk says {@link #PENDING} (« en vérification »)
 * until the final event carries the verdict.
 */
public enum ControlState {

  /** Streamed chunk: the answer is still being verified. */
  PENDING,

  /** No control screened the answer. */
  NOT_CHECKED,

  /** Every active check passed. */
  VALIDATED,

  /** Served on the deterministic tiers alone, the judge being unavailable. */
  DEGRADED
}
