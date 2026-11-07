package com.krizaka.sovkora.tools.application.service;

import static com.krizaka.sovkora.tools.application.service.LawReviewFixture.ERIC;
import static com.krizaka.sovkora.tools.application.service.LawReviewFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal.Code;
import com.krizaka.sovkora.tools.domain.model.law.ProposalStatus;
import com.krizaka.sovkora.tools.domain.model.law.ProposedRule;
import com.krizaka.sovkora.tools.domain.model.law.RuleProposal;
import com.krizaka.sovkora.tools.domain.model.law.VetReason;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The decisions of the review queue (ADR-089 §5 step 3, §8): approve, reject with a reason, and the
 * edit vetted again. A status a decision does not apply to is refused by name, never written.
 */
class RuleProposalReviewTest {

  private final LawReviewFixture law = new LawReviewFixture();
  private final RuleProposalService proposals = law.proposals;
  private final RuleProposalReviewService review =
      new RuleProposalReviewService(proposals, law.lookups, law.settings);

  @Test
  void approve_records_who_when_and_the_note() {
    RuleProposal proposed = law.recordVisitNotice().getFirst();

    RuleProposal approved = proposals.approve(proposed.id(), ERIC, "read beside art. 1931");

    assertThat(approved.status()).isEqualTo(ProposalStatus.APPROVED);
    assertThat(approved.reviewedBy()).isEqualTo(ERIC);
    assertThat(approved.reviewedAt()).isEqualTo(NOW);
    assertThat(approved.decisionNote()).isEqualTo("read beside art. 1931");
  }

  @Test
  void approve_applies_only_to_a_proposal_waiting_for_review() {
    RuleProposal proposed = law.recordVisitNotice().getFirst();
    proposals.approve(proposed.id(), ERIC, null);
    UUID id = proposed.id();

    assertThatThrownBy(() -> proposals.approve(id, ERIC, null))
        .isInstanceOfSatisfying(
            LawReviewRefusal.class,
            r -> assertThat(r.code()).isEqualTo(Code.PROPOSAL_NOT_REVIEWABLE));
    UUID missing = UUID.randomUUID();
    assertThatThrownBy(() -> proposals.approve(missing, ERIC, null))
        .isInstanceOfSatisfying(
            LawReviewRefusal.class, r -> assertThat(r.code()).isEqualTo(Code.PROPOSAL_NOT_FOUND));
  }

  @Test
  void a_rejection_says_why_and_may_follow_an_approval() {
    RuleProposal proposed = law.recordVisitNotice().getFirst();
    UUID id = proposed.id();

    assertThatThrownBy(() -> proposals.reject(id, ERIC, " "))
        .isInstanceOf(IllegalArgumentException.class);
    proposals.approve(id, ERIC, null);
    RuleProposal rejected = proposals.reject(id, ERIC, "the fixture pair does not read the delay");

    assertThat(rejected.status()).isEqualTo(ProposalStatus.REJECTED);
    assertThat(rejected.decisionNote()).isEqualTo("the fixture pair does not read the delay");
    assertThatThrownBy(() -> proposals.reject(id, ERIC, "again"))
        .isInstanceOfSatisfying(
            LawReviewRefusal.class,
            r -> assertThat(r.code()).isEqualTo(Code.PROPOSAL_NOT_REVIEWABLE));
  }

  @Test
  void a_review_names_its_reviewer() {
    UUID id = law.recordVisitNotice().getFirst().id();

    assertThatThrownBy(() -> proposals.approve(id, " ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a proposal edited after approval returns to PROPOSED (ADR-089 §5)")
  void an_edit_after_approval_returns_to_review() {
    RuleProposal approved = law.approvedVisitNotice().getFirst();

    RuleProposal edited =
        review.edit(
            approved.id(), LawReviewFixture.retitled(approved.rule(), "Avis de visite : délai"));

    assertThat(edited.status()).isEqualTo(ProposalStatus.PROPOSED);
    assertThat(edited.proposalRevision()).isEqualTo(approved.proposalRevision() + 1);
    assertThat(edited.rule().title()).isEqualTo("Avis de visite : délai");
    assertThat(edited.derivationRunId()).isEqualTo(approved.derivationRunId());
    assertThat(edited.vetReport().asOf()).isEqualTo(approved.vetReport().asOf());
    assertThat(proposals.find(approved.id()).orElseThrow().status())
        .isEqualTo(ProposalStatus.SUPERSEDED);
  }

  @Test
  void an_edit_the_vet_refuses_is_recorded_rejected_and_leaves_the_previous_one() {
    RuleProposal approved = law.approvedVisitNotice().getFirst();

    RuleProposal edited = review.edit(approved.id(), LawReviewFixture.misquoted(approved.rule()));

    assertThat(edited.status()).isEqualTo(ProposalStatus.REJECTED_AUTO);
    assertThat(edited.vetReport().reasons()).contains(VetReason.QUOTE_NOT_IN_ARTICLE);
    assertThat(proposals.find(approved.id()).orElseThrow().status())
        .isEqualTo(ProposalStatus.APPROVED);
  }

  @Test
  void an_edit_keeps_its_rule_id_and_applies_to_the_newest_revision_only() {
    RuleProposal first = law.recordVisitNotice().getFirst();
    UUID id = first.id();
    ProposedRule r = first.rule();
    ProposedRule otherId =
        new ProposedRule(
            "QC-OTHER-001",
            r.title(),
            r.severity(),
            r.method(),
            r.subject(),
            r.check(),
            r.question(),
            r.citation(),
            r.sourceUrl(),
            r.onPass(),
            r.onFail(),
            r.fixtures(),
            r.law());

    assertThatThrownBy(() -> review.edit(id, otherId)).isInstanceOf(IllegalArgumentException.class);
    review.edit(id, LawReviewFixture.retitled(r, "Nouveau titre"));
    ProposedRule again = LawReviewFixture.retitled(r, "Encore");
    assertThatThrownBy(() -> review.edit(id, again))
        .isInstanceOfSatisfying(
            LawReviewRefusal.class,
            refusal -> assertThat(refusal.code()).isEqualTo(Code.PROPOSAL_NOT_EDITABLE));
  }
}
