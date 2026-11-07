package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;
import java.util.UUID;

/**
 * Meters an external check (sovkora-engine#84): one {@code EXTERNAL_CHECK} operation of unit {@code
 * CALL} (ADR-087), priced by the pricebook row of the purpose, held against the run budget (ADR-033
 * hold then settle; the run budget of ADR-088 when it lands). A refused call takes no hold; a call
 * the budget cannot hold is refused before the provider is asked.
 */
public interface CheckMeter {

  /** A call held against the run budget. */
  interface Hold {

    /** The provider answered: one {@code CALL} is charged. */
    void settle();

    /**
     * The provider failed or timed out: nothing is charged ({@code calls: 0}).
     *
     * @param reason why, with no personal data
     */
    void release(String reason);
  }

  /** The run budget, or the credits, cannot hold the call. */
  class BudgetExhaustedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the refusal.
     *
     * @param message what was refused, with no personal data
     */
    public BudgetExhaustedException(String message) {
      super(message);
    }
  }

  /**
   * Holds one call.
   *
   * @param runId the run, or {@code null} for a platform call
   * @param actorId who is billed, or {@code null}
   * @param provider the connector's provider name
   * @param purpose the purpose, the pricebook row
   * @return the hold
   * @throws BudgetExhaustedException when the budget cannot hold the call
   */
  Hold hold(UUID runId, String actorId, String provider, CheckPurpose purpose);
}
