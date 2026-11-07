package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.control.ControlVerdictRecord;

/**
 * Persists a control verdict through the transactional outbox, in the transaction of the run's
 * state (ADR-092 §4 rule 7). A recorded verdict is never lost with a rolled-back run, nor kept
 * without it.
 */
@FunctionalInterface
public interface ControlVerdictRecorder {

  /**
   * Records a verdict.
   *
   * @param verdict what the audit keeps
   */
  void record(ControlVerdictRecord verdict);
}
