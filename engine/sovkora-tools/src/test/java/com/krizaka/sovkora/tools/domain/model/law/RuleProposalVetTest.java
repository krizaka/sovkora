package com.krizaka.sovkora.tools.domain.model.law;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.tools.domain.model.law.DerivationFixtures.Planted;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code vet} (ADR-089 §3): each rejection reason has its planted proposal, rejected for exactly
 * that reason, and the sound rules of the visit notice pass. The corpus is art. 1931 to 1933 of the
 * Civil Code of Québec as the official XML gives them.
 */
class RuleProposalVetTest {

  private static RuleProposalVet vet(int maxRules) {
    return new RuleProposalVet(
        DerivationFixtures.visitCorpus(), DerivationFixtures.NOTICE_DAY, maxRules);
  }

  static Stream<Planted> planted() {
    return DerivationFixtures.planted().stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("planted")
  @DisplayName("a planted proposal is rejected for exactly its reason, and the others pass")
  void each_planted_proposal_is_rejected_for_exactly_its_reason(Planted planted) {
    List<VettedProposal> vetted = vet(planted.maxRules()).vet(planted.rules());

    for (VettedProposal proposal : vetted) {
      if (proposal.rule().ruleId().equals(planted.plantedRuleId())) {
        assertThat(proposal.status()).isEqualTo(ProposalStatus.REJECTED_AUTO);
        assertThat(proposal.report().reasons()).containsExactly(planted.reason());
        assertThat(proposal.report().findings().getFirst().detail()).isNotBlank();
      } else {
        assertThat(proposal.report().findings()).isEmpty();
        assertThat(proposal.status()).isEqualTo(ProposalStatus.PROPOSED);
      }
    }
  }

  @Test
  @DisplayName("every reason of vet has at least one planted proposal")
  void every_reason_is_planted() {
    assertThat(DerivationFixtures.planted())
        .extracting(Planted::reason)
        .containsAll(List.of(VetReason.values()));
  }

  @Test
  @DisplayName("the golden case: the visit notice's derived rules pass vet, and cite 1931 to 1933")
  void the_visit_notice_rules_pass() {
    List<ProposedRule> rules = new ArrayList<>();
    DerivationFixtures.visitAnswers().values().forEach(rules::addAll);

    List<VettedProposal> vetted = vet(30).vet(rules);

    assertThat(vetted).hasSize(4);
    assertThat(vetted)
        .allSatisfy(
            v -> {
              assertThat(v.report().findings()).as(v.rule().ruleId()).isEmpty();
              assertThat(v.report().vetVersion()).isEqualTo(RuleProposalVet.VERSION);
              assertThat(v.report().asOf()).isEqualTo("2026-10-20");
            });
    assertThat(vetted)
        .flatExtracting(v -> v.rule().law())
        .extracting(LawBlock::ref)
        .containsOnly("1931", "1932", "1933");
  }

  @Test
  @DisplayName("a recorded answer citing an article the corpus does not hold is REJECTED_AUTO")
  void an_article_the_corpus_does_not_hold_is_rejected() {
    List<VettedProposal> vetted = vet(30).vet(DerivationFixtures.outsideCorpusAnswer());

    assertThat(vetted.getFirst().status()).isEqualTo(ProposalStatus.PROPOSED);
    assertThat(vetted.get(1).status()).isEqualTo(ProposalStatus.REJECTED_AUTO);
    assertThat(vetted.get(1).report().reasons()).containsExactly(VetReason.LAW_VERSION_UNKNOWN);
  }

  @Test
  @DisplayName("a block naming a version other than the one in force is LAW_VERSION_UNKNOWN")
  void another_version_of_the_article_is_unknown_for_the_day() {
    ProposedRule sound = DerivationFixtures.visitAnswers().get("1931").getFirst();
    LawBlock block = sound.law().getFirst();
    ProposedRule otherVersion =
        withLaw(
            sound,
            new LawBlock(
                block.instrument(), block.ref(), block.language(), "a".repeat(64), block.quote()));

    VetReport report = vet(30).vet(List.of(otherVersion)).getFirst().report();

    assertThat(report.reasons()).containsExactly(VetReason.LAW_VERSION_UNKNOWN);
    assertThat(report.findings().getFirst().detail()).contains("not the one in force");
  }

  @Test
  @DisplayName("a number written in words is supported by its digits, and a missing one is not")
  void numbers_are_compared_by_value() {
    assertThat(RuleFigures.of("Préavis de vingt-quatre heures")).containsExactly("24");
    assertThat(RuleFigures.of("au moins trois mois, à 9 h")).containsExactlyInAnyOrder("3", "9");
    // « un » and « one » are articles far more often than numbers.
    assertThat(RuleFigures.of("un préavis, one notice")).isEmpty();
    assertThat(RuleFigures.of("Twenty-four hours")).containsExactly("24");
  }

  @Test
  @DisplayName("a numeric check, a duplicated id or a missing fixture side is SCHEMA_INVALID")
  void the_shape_is_the_ruleset_rule_and_nothing_else() {
    ProposedRule sound = DerivationFixtures.visitAnswers().get("1931").getFirst();
    ProposedRule numeric =
        withCheck(sound, new RuleCheck("NUMERIC_MIN", "(\\d+)\\s*heures"), "QC-X-NUM");
    ProposedRule oneSided =
        new ProposedRule(
            "QC-X-SIDE",
            sound.title(),
            sound.severity(),
            sound.method(),
            sound.subject(),
            sound.check(),
            sound.question(),
            sound.citation(),
            sound.sourceUrl(),
            sound.onPass(),
            sound.onFail(),
            new RuleFixtures(sound.fixtures().pass(), List.of()),
            sound.law());

    List<VettedProposal> vetted = vet(30).vet(List.of(sound, sound, numeric, oneSided));

    assertThat(vetted.getFirst().status()).isEqualTo(ProposalStatus.PROPOSED);
    assertThat(vetted.subList(1, 4))
        .allSatisfy(
            v -> assertThat(v.report().reasons()).containsExactly(VetReason.SCHEMA_INVALID));
    assertThat(vetted.get(1).report().findings().getFirst().detail()).contains("proposed twice");
  }

  @Test
  @DisplayName("patterns the worker cannot run the same way are REGEX_INVALID")
  void patterns_must_run_the_same_way_in_the_worker() {
    for (String pattern :
        List.of(
            "\\p{L}+\\s+24\\s*heures",
            "24(?i)\\s*heures",
            "24\\s*+heures",
            "(?>24)\\s*heures",
            "[a-z&&[^x]]24\\s*heures",
            "(\\w+\\s?)+24")) {
      assertThat(WorkerRegex.problem(pattern)).as(pattern).isPresent();
    }
    for (String pattern :
        List.of(
            "(?i)\\b24\\s*(?:h\\b|heures?)",
            "(?<!aucun )d[ée]p[ôo]ts?",
            "(?=x)24",
            "[*+]24",
            "(?i:avis)\\s+24")) {
      assertThat(WorkerRegex.problem(pattern)).as(pattern).isEmpty();
    }
  }

  @Test
  @DisplayName("nested unbounded quantifiers are found by a scan, as the pack's linter finds them")
  void nested_unbounded_quantifiers_are_refused() {
    for (String bad : List.of("(a+)+", "(\\w*\\s?)*", "(?:x+y)+", "(ab*){2,}", "((a)+b)*")) {
      assertThat(WorkerRegex.nestsUnboundedQuantifiers(bad)).as(bad).isTrue();
    }
    for (String good :
        List.of(
            "(?i)d[ée]p[ôo]ts?\\s+(?:de\\s+)?garantie",
            "(\\d{1,2})\\s+(janvier|f[ée]vrier)",
            "(?:ab)+",
            "a+b*",
            "[(+]+x",
            "(a+){2,3}",
            "\\(a+\\)+")) {
      assertThat(WorkerRegex.nestsUnboundedQuantifiers(good)).as(good).isFalse();
    }
  }

  @Test
  @DisplayName("a rule id is UPPER-CASE-WITH-DASHES")
  void rule_ids_are_upper_case_with_dashes() {
    ProposedRule sound = DerivationFixtures.visitAnswers().get("1931").getFirst();
    for (String bad :
        List.of("qc-visit-1", "QCVISIT", "QC--VISIT", "QC-VISIT-", "-QC-1", "QC_VISIT-1")) {
      assertThat(
              vet(30)
                  .vet(List.of(withCheck(sound, sound.check(), bad)))
                  .getFirst()
                  .report()
                  .reasons())
          .as(bad)
          .containsExactly(VetReason.SCHEMA_INVALID);
    }
  }

  @Test
  @DisplayName("a pattern is read with Unicode classes and case folding, as Python reads it")
  void patterns_are_unicode_aware() {
    assertThat(WorkerRegex.finds("(?i)préavis", "PRÉAVIS")).isTrue();
    assertThat(WorkerRegex.finds("\\bheures\\b", "24 heures")).isTrue();
    assertThat(WorkerRegex.finds("^\\w+$", "état")).isTrue();
  }

  private static ProposedRule withLaw(ProposedRule rule, LawBlock block) {
    return new ProposedRule(
        rule.ruleId(),
        rule.title(),
        rule.severity(),
        rule.method(),
        rule.subject(),
        rule.check(),
        rule.question(),
        rule.citation(),
        rule.sourceUrl(),
        rule.onPass(),
        rule.onFail(),
        rule.fixtures(),
        List.of(block));
  }

  private static ProposedRule withCheck(ProposedRule rule, RuleCheck check, String ruleId) {
    return new ProposedRule(
        ruleId,
        rule.title(),
        rule.severity(),
        rule.method(),
        rule.subject(),
        check,
        rule.question(),
        rule.citation(),
        rule.sourceUrl(),
        rule.onPass(),
        rule.onFail(),
        rule.fixtures(),
        rule.law());
  }
}
