package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.tools.domain.model.law.ProposalStatus;
import com.krizaka.sovkora.tools.domain.model.law.RuleProposal;
import com.krizaka.sovkora.tools.domain.model.law.VettedProposal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code rule_proposal} (job-service): every rule a derivation proposed, vetted, one row per
 * revision. It takes only {@link VettedProposal}s, so nothing reaches it without the vet; only
 * {@code RuleProposalService} calls it (rule LAW-004).
 */
public interface RuleProposalStore {

  /**
   * Writes the vetted rules of one derivation, in one transaction, locked per jurisdiction and kind
   * so that two derivations of the same kind cannot take the same revision. Each rule gets the next
   * {@code proposal_revision} of its rule id; the unreviewed {@code PROPOSED} revisions of the same
   * rule become {@code SUPERSEDED}.
   *
   * @param jurisdiction the jurisdiction
   * @param kindId the kind
   * @param derivationRunId the derivation
   * @param proposals the vetted rules, in order
   * @param at the instant written
   * @return the rows written, in the same order
   */
  List<RuleProposal> record(
      String jurisdiction,
      String kindId,
      UUID derivationRunId,
      List<VettedProposal> proposals,
      Instant at);

  /**
   * One row.
   *
   * @param id the row id
   * @return the row, if any
   */
  Optional<RuleProposal> find(UUID id);

  /**
   * The queue, newest first.
   *
   * @param status only this status, or every status when null
   * @param kindId only this kind, or every kind when null
   * @return the rows
   */
  List<RuleProposal> list(ProposalStatus status, String kindId);

  /**
   * Records a review decision, only if the row is still in one of the expected statuses: a
   * conditional write, so two reviewers cannot both decide on one row.
   *
   * @param id the row
   * @param from the statuses the decision applies to
   * @param to the decided status
   * @param reviewedBy who decided
   * @param note the note or the reason
   * @param at when
   * @return the row as written, or empty when it is missing or no longer in {@code from}
   */
  Optional<RuleProposal> decide(
      UUID id,
      Set<ProposalStatus> from,
      ProposalStatus to,
      String reviewedBy,
      String note,
      Instant at);

  /**
   * Writes an edit of a proposal as its rule's next revision, under the kind's lock. The previous
   * row must still be in one of {@code editable}; when the edit passed the vet, the {@code
   * PROPOSED} and {@code APPROVED} revisions of the rule become {@code SUPERSEDED} (an edit after
   * approval returns to review); a rejected edit supersedes nothing.
   *
   * @param previousId the row edited
   * @param editable the statuses an edit applies to
   * @param edited the edited rule, vetted again
   * @param at when
   * @return the new revision, or empty when the previous row is missing or no longer editable
   */
  Optional<RuleProposal> revise(
      UUID previousId, Set<ProposalStatus> editable, VettedProposal edited, Instant at);

  /**
   * Promotes proposals in one transaction: it locks the rows, hands them to {@code step} (which
   * checks them again and opens the pull request), and then writes the pull request's address on
   * every row. A failed step writes nothing; two promotions of the same rows cannot both open one.
   *
   * @param ids the rows
   * @param step the checks and the opening, given the locked rows in the order of {@code ids}
   * @return the pull request's address
   */
  String promote(List<UUID> ids, PromotionStep step);

  /** The checks and the opening of a promotion, run while its rows are locked. */
  @FunctionalInterface
  interface PromotionStep {

    /**
     * Checks the locked rows and opens the pull request.
     *
     * @param locked the rows, missing ones left out
     * @return the pull request's address
     */
    String open(List<RuleProposal> locked);
  }
}
