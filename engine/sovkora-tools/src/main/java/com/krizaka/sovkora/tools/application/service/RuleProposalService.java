package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.RuleProposalStore;
import com.krizaka.sovkora.tools.application.port.law.RuleProposalStore.PromotionStep;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal.Code;
import com.krizaka.sovkora.tools.domain.model.law.ProposalStatus;
import com.krizaka.sovkora.tools.domain.model.law.RuleProposal;
import com.krizaka.sovkora.tools.domain.model.law.VettedProposal;
import java.time.Clock;
import java.util.*;

/**
 * The writes and reads of {@code rule_proposal} (ADR-089 §3): the only door to the table. A rule
 * enters only vetted ({@link VettedProposal}), as {@code PROPOSED} or {@code REJECTED_AUTO}; a
 * proposal never becomes a ruleset here: the review decisions and the promotion of stage 5 are
 * recorded through it, and the ruleset itself is the pull request {@link RulesetPromotionService}
 * opens.
 */
public class RuleProposalService {

  /** Approve applies to a proposal waiting for review. */
  private static final Set<ProposalStatus> APPROVABLE = EnumSet.of(ProposalStatus.PROPOSED);

  /** Reject applies to a proposal waiting for review or approved, not yet in a pull request. */
  private static final Set<ProposalStatus> REJECTABLE =
      EnumSet.of(ProposalStatus.PROPOSED, ProposalStatus.APPROVED);

  /**
   * An edit applies to any revision a reviewer may still change: not a promoted one, and not a
   * superseded one (the newest revision is edited).
   */
  private static final Set<ProposalStatus> EDITABLE =
      EnumSet.of(
          ProposalStatus.PROPOSED,
          ProposalStatus.APPROVED,
          ProposalStatus.REJECTED,
          ProposalStatus.REJECTED_AUTO);

  private final RuleProposalStore store;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param store {@code rule_proposal}
   * @param clock the clock (injected)
   */
  public RuleProposalService(RuleProposalStore store, Clock clock) {
    this.store = Objects.requireNonNull(store, "RuleProposalStore");
    this.clock = Objects.requireNonNull(clock, "Clock");
  }

  /**
   * Records the vetted rules of one derivation.
   *
   * @param jurisdiction the jurisdiction, such as {@code QC}
   * @param kindId the kind the rules are for
   * @param derivationRunId the derivation
   * @param proposals the vetted rules, in order; empty writes nothing
   * @return the rows written, in order
   * @throws IllegalArgumentException when the jurisdiction or the kind is blank
   */
  public List<RuleProposal> record(
      String jurisdiction, String kindId, UUID derivationRunId, List<VettedProposal> proposals) {
    if (jurisdiction == null || jurisdiction.isBlank() || kindId == null || kindId.isBlank()) {
      throw new IllegalArgumentException("a proposal names its jurisdiction and its kind");
    }
    Objects.requireNonNull(derivationRunId, "derivationRunId");
    if (proposals == null || proposals.isEmpty()) {
      return List.of();
    }
    return store.record(
        jurisdiction.trim().toUpperCase(Locale.ROOT),
        kindId.trim(),
        derivationRunId,
        List.copyOf(proposals),
        clock.instant());
  }

  /**
   * One proposal.
   *
   * @param id the row id
   * @return the row, if any
   */
  public Optional<RuleProposal> find(UUID id) {
    return store.find(Objects.requireNonNull(id, "id"));
  }

  /**
   * The review queue.
   *
   * @param status only this status, or all when null
   * @param kindId only this kind, or all when null or blank
   * @return the rows, newest first
   */
  public List<RuleProposal> list(ProposalStatus status, String kindId) {
    return store.list(status, kindId == null || kindId.isBlank() ? null : kindId.trim());
  }

  /**
   * Approves a proposal waiting for review (ADR-089 §5 step 3).
   *
   * @param id the row
   * @param reviewer who approves
   * @param note an optional note
   * @return the row, {@code APPROVED}
   * @throws LawReviewRefusal {@code PROPOSAL_NOT_FOUND}, or {@code PROPOSAL_NOT_REVIEWABLE} unless
   *     it was {@code PROPOSED}
   */
  public RuleProposal approve(UUID id, String reviewer, String note) {
    requireReviewer(reviewer);
    return store
        .decide(
            Objects.requireNonNull(id, "id"),
            APPROVABLE,
            ProposalStatus.APPROVED,
            reviewer.trim(),
            blankToNull(note),
            clock.instant())
        .orElseThrow(() -> refusal(id, Code.PROPOSAL_NOT_REVIEWABLE));
  }

  /**
   * Rejects a proposal, with its reason.
   *
   * @param id the row
   * @param reviewer who rejects
   * @param reason why, required
   * @return the row, {@code REJECTED}
   * @throws IllegalArgumentException when the reason is blank
   * @throws LawReviewRefusal {@code PROPOSAL_NOT_FOUND}, or {@code PROPOSAL_NOT_REVIEWABLE} unless
   *     it was {@code PROPOSED} or {@code APPROVED} and in no pull request
   */
  public RuleProposal reject(UUID id, String reviewer, String reason) {
    requireReviewer(reviewer);
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("a rejection says why");
    }
    return store
        .decide(
            Objects.requireNonNull(id, "id"),
            REJECTABLE,
            ProposalStatus.REJECTED,
            reviewer.trim(),
            reason,
            clock.instant())
        .orElseThrow(() -> refusal(id, Code.PROPOSAL_NOT_REVIEWABLE));
  }

  /**
   * Records an edit as the rule's next revision. Only {@link RuleProposalReviewService} makes the
   * vetted edit.
   *
   * @param id the row edited
   * @param edited the edited rule, vetted again
   * @return the new revision
   * @throws LawReviewRefusal {@code PROPOSAL_NOT_FOUND}, or {@code PROPOSAL_NOT_EDITABLE} for a
   *     promoted or superseded revision
   */
  public RuleProposal revise(UUID id, VettedProposal edited) {
    Objects.requireNonNull(edited, "edited");
    return store
        .revise(Objects.requireNonNull(id, "id"), EDITABLE, edited, clock.instant())
        .orElseThrow(() -> refusal(id, Code.PROPOSAL_NOT_EDITABLE));
  }

  /**
   * Promotes proposals: their rows are locked while {@code step} checks them and opens the pull
   * request, then they carry its address. Only {@link RulesetPromotionService} promotes.
   *
   * @param ids the rows
   * @param step the checks and the opening
   * @return the pull request's address
   */
  public String promote(List<UUID> ids, PromotionStep step) {
    if (ids == null || ids.isEmpty()) {
      throw new IllegalArgumentException("a promotion names its proposals");
    }
    return store.promote(List.copyOf(ids), Objects.requireNonNull(step, "step"));
  }

  private LawReviewRefusal refusal(UUID id, Code code) {
    return store.find(id).isEmpty()
        ? new LawReviewRefusal(Code.PROPOSAL_NOT_FOUND, "no proposal " + id)
        : new LawReviewRefusal(code, "proposal " + id + " is not in a status this applies to");
  }

  private static void requireReviewer(String reviewer) {
    if (reviewer == null || reviewer.isBlank()) {
      throw new IllegalArgumentException("a review names its reviewer");
    }
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text;
  }
}
