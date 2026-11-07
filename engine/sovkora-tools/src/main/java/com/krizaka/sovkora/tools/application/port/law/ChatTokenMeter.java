package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;

/**
 * Meters the chat tokens of a grounded call (ADR-087 §5, ADR-033): the model call is priced like
 * any chat turn, by the pricebook row of the {@code CHAT} capability, in the kilotokens the
 * provider reported. It is held before the model is asked and settled once with the measured usage,
 * so the price of a grounded call is the {@code CALL} of its source plus its tokens, each counted
 * once.
 *
 * <p>The reservation's estimate is what a cap is checked against; the settled price is the ledger's
 * (measurements only, never a unit nor a price).
 */
public interface ChatTokenMeter {

  /** The tokens of one model call, held against the credits. */
  interface Hold {

    /**
     * What the ledger reserved for this call.
     *
     * @return the estimate, in credits; 0 when nothing is metered
     */
    long estimatedCredits();

    /**
     * Settles the call with what the provider reported. A call whose provider reported nothing is
     * released instead: an estimate is never billed as if it were a measurement.
     *
     * @param usage the tokens of the call
     */
    void settle(TokenUsage usage);

    /**
     * Releases the hold: the call did not run, nothing is charged.
     *
     * @param reason why
     */
    void release(String reason);
  }

  /**
   * Holds the tokens of one model call.
   *
   * @param runId the run, as its identifier text
   * @param actorId who is billed
   * @return the hold
   * @throws ResearchMeter.BudgetExceededException when there is no billable actor or the credits
   *     cannot hold the call
   */
  Hold hold(String runId, String actorId);
}
