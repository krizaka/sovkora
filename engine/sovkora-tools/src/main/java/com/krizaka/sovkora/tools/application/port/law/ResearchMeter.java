package com.krizaka.sovkora.tools.application.port.law;

/**
 * Meters a grounded research call (ADR-087 §5): one billable {@code RESEARCH} operation, unit
 * {@code CALL}, priced by the pricebook row of the provider key, counted against the run budget
 * (ADR-088). A call that the budget cannot hold is refused before anything is asked.
 */
public interface ResearchMeter {

  /** A call held against the run budget. */
  interface Hold {

    /**
     * What the ledger reserved for this call, for a cap to be checked against.
     *
     * @return the estimate, in credits; 0 when the meter cannot tell
     */
    default long estimatedCredits() {
      return 0L;
    }

    /** Settles the call: it ran, the price of one {@code CALL} is charged. */
    void settle();

    /**
     * Releases the hold: the call did not run, nothing is charged.
     *
     * @param reason why
     */
    void release(String reason);
  }

  /** The run budget cannot hold the call. */
  class BudgetExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the refusal.
     *
     * @param message what was refused
     */
    public BudgetExceededException(String message) {
      super(message);
    }
  }

  /**
   * Holds one call against the run budget.
   *
   * @param runId the run, as its identifier text
   * @param actorId who is billed
   * @param providerKey the provider of the source, the pricebook's {@code model_name}
   * @return the hold
   * @throws BudgetExceededException when the run budget (or the credits) cannot hold the call
   */
  Hold hold(String runId, String actorId, String providerKey);
}
