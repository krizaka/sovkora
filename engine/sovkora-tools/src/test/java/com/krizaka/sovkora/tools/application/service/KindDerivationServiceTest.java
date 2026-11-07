package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.tools.application.port.law.RulesetPack;
import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal.Code;
import com.krizaka.sovkora.tools.domain.model.law.RuleDerivationResult.Outcome;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** An administrator starts a derivation: the kind's targets and question come from the pack. */
class KindDerivationServiceTest {

  private static final LocalDate DAY = LocalDate.parse("2026-10-20");

  private final List<LawRetrievalRequest> asked = new ArrayList<>();
  private final RuleDerivationService derivations = mock(RuleDerivationService.class);

  @BeforeEach
  void recordTheRequests() {
    when(derivations.derive(any()))
        .thenAnswer(
            call -> {
              asked.add(call.getArgument(0));
              return RuleDerivationResult.nothing(Outcome.DERIVATION_OFF, null);
            });
  }

  private static RulesetPack pack(Optional<PackKind> kind) {
    return new RulesetPack() {
      @Override
      public Optional<PackKind> kind(String kindId) {
        return kind;
      }

      @Override
      public List<GoldenCase> goldenCases(String kindId) {
        return List.of();
      }

      @Override
      public OptionalInt rulesetRevision(String kindId, LocalDate effectiveFrom) {
        return OptionalInt.empty();
      }
    };
  }

  private static PackKind visitNotice() {
    return new PackKind(
        "qc-visit-notice",
        "QC",
        "KNOWN",
        List.of(new LawTargetRef("CCQ-1991", List.of("1931", "1932", "1933"))),
        Map.of("fr", "Quels articles régissent un avis de visite ?", "en", "Which articles?"));
  }

  @Test
  void the_derivation_reads_the_kind_from_the_pack_and_runs_under_a_new_run_id() {
    KindDerivationService service =
        new KindDerivationService(pack(Optional.of(visitNotice())), derivations);

    DerivationStarted started = service.start("qc", "qc-visit-notice", DAY, null, "eric-admin");

    LawRetrievalRequest request = asked.getFirst();
    assertThat(request.runId()).isEqualTo(started.runId());
    assertThat(request.language()).isEqualTo("fr");
    assertThat(request.question()).startsWith("Quels articles");
    assertThat(request.targets()).isEqualTo(visitNotice().targets());
    assertThat(request.actorId()).isEqualTo("eric-admin");
    assertThat(started.result().outcome()).isEqualTo(Outcome.DERIVATION_OFF);
    assertThat(started.proposed()).isZero();
  }

  @Test
  void a_kind_of_another_jurisdiction_or_with_no_question_is_refused() {
    KindDerivationService service =
        new KindDerivationService(pack(Optional.of(visitNotice())), derivations);

    assertThatThrownBy(() -> service.start("CA", "qc-visit-notice", DAY, "fr", "eric-admin"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.start("QC", "qc-visit-notice", DAY, "de", "eric-admin"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(asked).isEmpty();
  }

  @Test
  void an_unknown_kind_is_not_found_and_an_unconfigured_pack_is_derivation_off() {
    KindDerivationService unknown = new KindDerivationService(pack(Optional.empty()), derivations);
    RulesetPack off =
        new RulesetPack() {
          @Override
          public Optional<PackKind> kind(String kindId) {
            throw new LawReviewRefusal(Code.LAW_PROMOTION_OFF, "not configured");
          }

          @Override
          public List<GoldenCase> goldenCases(String kindId) {
            throw new LawReviewRefusal(Code.LAW_PROMOTION_OFF, "not configured");
          }

          @Override
          public OptionalInt rulesetRevision(String kindId, LocalDate effectiveFrom) {
            throw new LawReviewRefusal(Code.LAW_PROMOTION_OFF, "not configured");
          }
        };
    KindDerivationService unwired = new KindDerivationService(off, derivations);

    assertThatThrownBy(() -> unknown.start("QC", "qc-sublet-notice", DAY, "fr", "eric-admin"))
        .isInstanceOfSatisfying(
            LawReviewRefusal.class, r -> assertThat(r.code()).isEqualTo(Code.KIND_NOT_FOUND));
    assertThatThrownBy(() -> unwired.start("QC", "qc-visit-notice", DAY, "fr", "eric-admin"))
        .isInstanceOfSatisfying(
            LawReviewRefusal.class, r -> assertThat(r.code()).isEqualTo(Code.LAW_DERIVATION_OFF));
  }
}
