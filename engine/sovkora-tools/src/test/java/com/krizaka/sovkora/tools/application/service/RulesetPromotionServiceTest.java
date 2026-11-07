package com.krizaka.sovkora.tools.application.service;

import static com.krizaka.sovkora.tools.application.service.LawReviewFixture.ERIC;
import static com.krizaka.sovkora.tools.application.service.LawReviewFixture.KIND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.tools.application.port.law.RulesetPack;
import com.krizaka.sovkora.tools.application.port.law.RulesetPromotionPublisher;
import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal.Code;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stage 5 of ADR-089: approved proposals, golden cases and pinned hashes, then one pull request.
 * The publisher is recorded; nothing reaches GitHub. Every refusal is checked to open nothing.
 */
class RulesetPromotionServiceTest {

  /**
   * The day art. 1931 to 1933 of the fixture corpus took effect, as the official XML dates them.
   */
  private static final LocalDate IN_FORCE = LocalDate.parse("1999-04-01");

  private final LawReviewFixture law = new LawReviewFixture();
  private final FakePack pack = new FakePack();
  private final RecordingPublisher publisher = new RecordingPublisher();
  private final RulesetPromotionService promotion =
      new RulesetPromotionService(law.proposals, law.lookups, law.settings, pack, publisher);

  private static List<UUID> ids(List<RuleProposal> rows) {
    return rows.stream().map(RuleProposal::id).toList();
  }

  private void refused(Code code, ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(LawReviewRefusal.class, r -> assertThat(r.code()).isEqualTo(code));
    assertThat(publisher.opened).as("a refused promotion opens nothing").isEmpty();
  }

  @Test
  @DisplayName("a missing kind id is refused before anything is read")
  void a_missing_kind_is_refused() {
    assertThatThrownBy(() -> promotion.promote(null, List.of(), IN_FORCE, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kind id");
  }

  @Test
  @DisplayName("approved proposals become one pull request, their law pinned at the in-force day")
  void approved_proposals_open_one_pull_request() {
    List<RuleProposal> approved = law.approvedVisitNotice();

    PromotionResult result = promotion.promote(KIND, ids(approved), IN_FORCE, null);

    assertThat(result.prUrl()).isEqualTo(RecordingPublisher.URL);
    assertThat(result.rulesetPath())
        .isEqualTo("document-validation/rulesets/qc-visit-notice/1999-04-01.json");
    assertThat(result.revision()).isEqualTo(1);
    RulesetPromotion opened = publisher.opened.getFirst();
    assertThat(opened.rules()).hasSize(approved.size());
    assertThat(opened.rules())
        .flatExtracting(PromotedRule::law)
        .allSatisfy(
            (PinnedLaw block) -> {
              assertThat(block.inForceFrom()).isEqualTo(IN_FORCE);
              assertThat(block.officialUrl()).startsWith("https://");
              assertThat(block.quote()).hasSizeLessThanOrEqualTo(PinnedLaw.MAX_QUOTE);
            });
    assertThat(opened.articles()).extracting(PinnedArticle::ref).containsOnly("1931", "1932");
    assertThat(opened.provenance().proposalIds()).isEqualTo(ids(approved));
    assertThat(opened.provenance().derivationRunIds()).containsExactly(LawReviewFixture.RUN);
    assertThat(opened.provenance().reviewedBy()).containsExactly(ERIC);
    assertThat(opened.provenance().lawImportIds()).containsExactly(law.importId);
    assertThat(approved)
        .allSatisfy(
            p -> {
              RuleProposal now = law.proposals.find(p.id()).orElseThrow();
              assertThat(now.packsPrUrl()).isEqualTo(RecordingPublisher.URL);
              assertThat(now.status()).isEqualTo(ProposalStatus.APPROVED);
            });
  }

  @Test
  @DisplayName("promote refuses a proposal that is not approved")
  void an_unapproved_proposal_is_refused() {
    List<RuleProposal> proposed = law.recordVisitNotice();

    refused(
        Code.PROPOSAL_NOT_APPROVED, () -> promotion.promote(KIND, ids(proposed), IN_FORCE, null));
  }

  @Test
  @DisplayName("promote refuses a kind below law.review.min-golden-cases")
  void a_kind_below_the_golden_minimum_is_refused() {
    List<UUID> approved = ids(law.approvedVisitNotice());
    law.settings.minGoldenCases = 5;

    refused(Code.GOLDEN_MINIMUM_NOT_MET, () -> promotion.promote(KIND, approved, IN_FORCE, null));
  }

  @Test
  void the_golden_cases_need_a_clean_one_a_defective_one_and_the_blank_form() {
    List<UUID> approved = ids(law.approvedVisitNotice());
    pack.golden =
        List.of(
            new GoldenCase("fr-compliant"),
            new GoldenCase("en-compliant"),
            new GoldenCase("fr-defective"));

    refused(Code.GOLDEN_MINIMUM_NOT_MET, () -> promotion.promote(KIND, approved, IN_FORCE, null));
    assertThat(promotion.goldenCases(KIND).met()).isFalse();
    assertThat(promotion.goldenCases(KIND).blank()).isZero();
  }

  @Test
  void a_proposal_already_in_a_pull_request_is_not_promoted_twice() {
    List<UUID> approved = ids(law.approvedVisitNotice());
    promotion.promote(KIND, approved, IN_FORCE, null);
    publisher.opened.clear();

    refused(Code.ALREADY_IN_REVIEW, () -> promotion.promote(KIND, approved, IN_FORCE, null));
  }

  @Test
  void a_proposal_of_another_kind_is_refused() {
    List<UUID> approved = ids(law.approvedVisitNotice());

    refused(
        Code.KIND_MISMATCH,
        () -> promotion.promote("qc-condition-report", approved, IN_FORCE, null));
  }

  @Test
  void a_pinned_hash_not_in_force_on_the_ruleset_day_is_refused() {
    List<UUID> approved = ids(law.approvedVisitNotice());

    refused(
        Code.LAW_VERSION_MISMATCH,
        () -> promotion.promote(KIND, approved, LocalDate.parse("1990-01-01"), null));
  }

  @Test
  void a_quote_longer_than_a_ruleset_holds_is_refused() {
    RuleProposal first = law.recordVisitNotice().getFirst();
    RuleProposal longer =
        new RuleProposal(
            first.id(),
            first.jurisdiction(),
            first.kindId(),
            first.ruleId(),
            first.proposalRevision(),
            LawReviewFixture.longQuote(first.rule()),
            first.derivationRunId(),
            ProposalStatus.APPROVED,
            first.vetReport(),
            ERIC,
            LawReviewFixture.NOW,
            null,
            null,
            first.createdAt());
    law.store.put(longer);

    refused(
        Code.QUOTE_TOO_LONG, () -> promotion.promote(KIND, List.of(first.id()), IN_FORCE, null));
  }

  @Test
  void a_new_revision_of_an_existing_version_says_what_it_corrects() {
    List<UUID> approved = ids(law.approvedVisitNotice());
    pack.revision = OptionalInt.of(1);

    assertThatThrownBy(() -> promotion.promote(KIND, approved, IN_FORCE, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(publisher.opened).isEmpty();
    assertThat(
            promotion
                .promote(KIND, approved, IN_FORCE, "the 1932 quote is the whole first sentence")
                .revision())
        .isEqualTo(2);
    assertThat(publisher.opened.getFirst().revisionNote()).contains("1932");
  }

  @Test
  void a_promotion_names_its_proposals_and_its_day() {
    List<UUID> approved = ids(law.approvedVisitNotice());
    List<UUID> twice = List.of(approved.getFirst(), approved.getFirst());

    assertThatThrownBy(() -> promotion.promote(KIND, List.of(), IN_FORCE, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> promotion.promote(KIND, approved, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> promotion.promote(KIND, twice, IN_FORCE, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> promotion.promote("../../etc", approved, IN_FORCE, null))
        .isInstanceOf(IllegalArgumentException.class);
    refused(
        Code.PROPOSAL_NOT_FOUND,
        () -> promotion.promote(KIND, List.of(UUID.randomUUID()), IN_FORCE, null));
  }

  @Test
  @DisplayName("the status is checked again while the rows are locked")
  void a_rejection_between_the_check_and_the_lock_opens_nothing() {
    List<RuleProposal> approved = law.approvedVisitNotice();
    RuleProposalService racing =
        new RuleProposalService(law.store, law.clock) {
          @Override
          public String promote(
              List<UUID> ids,
              com.krizaka.sovkora.tools.application.port.law.RuleProposalStore.PromotionStep step) {
            // Another reviewer rejects one of them just before the rows are locked.
            law.store.rows.replaceAll(
                r ->
                    r.id().equals(ids.getFirst())
                        ? new RuleProposal(
                            r.id(),
                            r.jurisdiction(),
                            r.kindId(),
                            r.ruleId(),
                            r.proposalRevision(),
                            r.rule(),
                            r.derivationRunId(),
                            ProposalStatus.REJECTED,
                            r.vetReport(),
                            ERIC,
                            r.reviewedAt(),
                            "on second thought",
                            null,
                            r.createdAt())
                        : r);
            return super.promote(ids, step);
          }
        };
    RulesetPromotionService service =
        new RulesetPromotionService(racing, law.lookups, law.settings, pack, publisher);

    refused(Code.PROPOSAL_NOT_APPROVED, () -> service.promote(KIND, ids(approved), IN_FORCE, null));
    assertThat(law.store.promotions).isEmpty();
  }

  /** The pack of the visit notice: four golden cases, no ruleset yet. */
  static final class FakePack implements RulesetPack {
    List<GoldenCase> golden =
        List.of(
            new GoldenCase("fr-compliant"),
            new GoldenCase("fr-defective"),
            new GoldenCase("en-defective"),
            new GoldenCase("fr-blank"));
    OptionalInt revision = OptionalInt.empty();

    @Override
    public Optional<PackKind> kind(String kindId) {
      return Optional.empty();
    }

    @Override
    public List<GoldenCase> goldenCases(String kindId) {
      return golden;
    }

    @Override
    public OptionalInt rulesetRevision(String kindId, LocalDate effectiveFrom) {
      return revision;
    }
  }

  /** Records what would be opened; GitHub is never called. */
  static final class RecordingPublisher implements RulesetPromotionPublisher {
    static final String URL = "https://github.com/krizaka/sovkora-packs/pull/4242";
    final List<RulesetPromotion> opened = new ArrayList<>();

    @Override
    public Opened open(RulesetPromotion promotion) {
      opened.add(promotion);
      return new Opened(URL, "law-promote/" + promotion.kindId() + "/test");
    }
  }
}
