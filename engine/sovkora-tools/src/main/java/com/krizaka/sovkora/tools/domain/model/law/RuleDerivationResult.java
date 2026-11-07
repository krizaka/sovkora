package com.krizaka.sovkora.tools.domain.model.law;

import java.util.List;
import java.util.Objects;

/**
 * What one derivation did (ADR-089 §3): the rows it wrote, what it spent, and the articles it did
 * not get to.
 *
 * @param outcome how it ended
 * @param retrieval how the retrieval of stage 2 ended; null when none was made
 * @param proposals the {@code rule_proposal} rows written, {@code PROPOSED} and {@code
 *     REJECTED_AUTO}, in the order proposed
 * @param creditsSpent what the model calls were charged
 * @param notDerived the articles with verified claims that no call was made for: the cap was
 *     reached, or the corpus no longer holds, on {@code asOf}, the text the claims were verified on
 */
public record RuleDerivationResult(
    Outcome outcome,
    LawRetrievalResult.Outcome retrieval,
    List<RuleProposal> proposals,
    long creditsSpent,
    List<CitedArticle> notDerived) {

  /** How a derivation ended. */
  public enum Outcome {
    /** {@code law.derive.max-credits} is 0: nothing was read, no model was called. */
    DERIVATION_OFF,
    /** The retrieval did not give articles to derive from (its outcome says why). */
    NOT_RETRIEVED,
    /** The retrieval holds no verified claim: nothing may be derived from a claim to validate. */
    NO_VERIFIED_CLAIMS,
    /** Every article with a verified claim was derived from. */
    DERIVED,
    /**
     * The next call would have passed {@code law.derive.max-credits}: the run stopped there. What
     * was derived before is vetted and recorded; {@code notDerived} lists the rest.
     */
    BUDGET_EXHAUSTED
  }

  /** Copies the lists. */
  public RuleDerivationResult {
    Objects.requireNonNull(outcome, "outcome");
    proposals = proposals == null ? List.of() : List.copyOf(proposals);
    notDerived = notDerived == null ? List.of() : List.copyOf(notDerived);
  }

  /**
   * A derivation that made no call.
   *
   * @param outcome why
   * @param retrieval the retrieval's outcome, or null
   * @return the result
   */
  public static RuleDerivationResult nothing(
      Outcome outcome, LawRetrievalResult.Outcome retrieval) {
    return new RuleDerivationResult(outcome, retrieval, List.of(), 0, List.of());
  }
}
