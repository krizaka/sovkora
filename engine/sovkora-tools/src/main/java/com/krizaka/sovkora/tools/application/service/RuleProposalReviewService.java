package com.krizaka.sovkora.tools.application.service;

import com.krizaka.sovkora.tools.application.port.law.LawSettings;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal.Code;
import com.krizaka.sovkora.tools.domain.model.law.ProposedRule;
import com.krizaka.sovkora.tools.domain.model.law.RuleProposal;
import com.krizaka.sovkora.tools.domain.model.law.RuleProposalVet;
import com.krizaka.sovkora.tools.domain.model.law.VettedProposal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The reviewer's edit of a proposal (ADR-089 §8, Edit): a new {@code proposal_revision}, vetted
 * again against the corpus of the day the proposal was vetted on. An edit goes through the same
 * gate as a derivation ({@link RuleProposalVet}), so a reviewer cannot write a rule the vet would
 * refuse as {@code PROPOSED}; an edit after approval returns to review.
 */
public class RuleProposalReviewService {

  private final RuleProposalService proposals;
  private final LawStatuteLookupService lookups;
  private final LawSettings settings;

  /**
   * Wires the service.
   *
   * @param proposals the door to {@code rule_proposal}
   * @param lookups the corpus at a day
   * @param settings the {@code law.*} settings
   */
  public RuleProposalReviewService(
      RuleProposalService proposals, LawStatuteLookupService lookups, LawSettings settings) {
    this.proposals = Objects.requireNonNull(proposals, "RuleProposalService");
    this.lookups = Objects.requireNonNull(lookups, "LawStatuteLookupService");
    this.settings = Objects.requireNonNull(settings, "LawSettings");
  }

  /**
   * Edits a proposal.
   *
   * @param id the row edited
   * @param edited the rule as the reviewer wrote it; it keeps its rule id
   * @return the new revision: {@code PROPOSED} when it passed the vet, else {@code REJECTED_AUTO}
   *     with its report
   * @throws IllegalArgumentException when the edit changes the rule id
   * @throws LawReviewRefusal {@code PROPOSAL_NOT_FOUND} or {@code PROPOSAL_NOT_EDITABLE}
   */
  public RuleProposal edit(UUID id, ProposedRule edited) {
    Objects.requireNonNull(edited, "edited");
    RuleProposal previous =
        proposals
            .find(id)
            .orElseThrow(() -> new LawReviewRefusal(Code.PROPOSAL_NOT_FOUND, "no proposal " + id));
    if (!previous.ruleId().equals(edited.ruleId())) {
      throw new IllegalArgumentException(
          "an edit keeps the rule id " + previous.ruleId() + "; a new rule is a new derivation");
    }
    LocalDate asOf = vettedOn(previous);
    VettedProposal vetted =
        new RuleProposalVet(
                lookups.inForceOn(previous.jurisdiction(), asOf),
                asOf,
                settings.deriveMaxRulesPerKind())
            .vet(List.of(edited))
            .getFirst();
    return proposals.revise(id, vetted);
  }

  /** The day the proposal was vetted on, which its edit is vetted on again. */
  private static LocalDate vettedOn(RuleProposal previous) {
    if (previous.vetReport() == null || previous.vetReport().asOf() == null) {
      throw new IllegalStateException("proposal " + previous.id() + " has no vet report");
    }
    return LocalDate.parse(previous.vetReport().asOf());
  }
}
