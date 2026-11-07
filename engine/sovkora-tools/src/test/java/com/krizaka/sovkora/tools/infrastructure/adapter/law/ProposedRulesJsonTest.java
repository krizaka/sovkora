package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.tools.domain.model.law.DerivationFixtures;
import com.krizaka.sovkora.tools.domain.model.law.ProposedRule;
import com.krizaka.sovkora.tools.domain.model.law.VetFinding;
import com.krizaka.sovkora.tools.domain.model.law.VetReason;
import com.krizaka.sovkora.tools.domain.model.law.VetReport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The JSON of a model answer and of the {@code rule_proposal} columns. */
class ProposedRulesJsonTest {

  @Test
  void an_answer_is_read_in_the_shape_of_a_ruleset_rule() {
    ProposedRule rule = DerivationFixtures.visitAnswers().get("1931").getFirst();

    assertThat(rule.ruleId()).isEqualTo("QC-VISIT-001");
    assertThat(rule.check().type()).isEqualTo("REQUIRED_PATTERN");
    assertThat(rule.fixtures().pass()).hasSize(2);
    assertThat(rule.law()).singleElement().satisfies(b -> assertThat(b.ref()).isEqualTo("1931"));
  }

  @Test
  void a_fixture_side_may_be_one_fragment_as_in_a_ruleset() {
    List<ProposedRule> rules =
        ProposedRulesJson.answer(
            "{\"rules\":[{\"ruleId\":\"QC-X-1\",\"fixtures\":{\"pass\":\"oui\",\"fail\":[\"non\"]}}]}");

    assertThat(rules.getFirst().fixtures().pass()).containsExactly("oui");
    assertThat(rules.getFirst().fixtures().fail()).containsExactly("non");
  }

  @Test
  void an_answer_outside_the_contract_is_refused() {
    assertThatThrownBy(() -> ProposedRulesJson.answer("Voici les règles : …"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProposedRulesJson.answer("{\"rule\": []}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_stored_rule_and_report_round_trip_and_absent_parts_are_left_out() {
    ProposedRule judgment = DerivationFixtures.visitAnswers().get("1933").getFirst();
    VetReport report =
        new VetReport("1", "2026-10-20", List.of(new VetFinding(VetReason.SUBJECT_MISSING, "x")));

    String json = ProposedRulesJson.write(judgment);

    assertThat(json).doesNotContain("\"check\"").doesNotContain("null");
    assertThat(ProposedRulesJson.rule(json)).isEqualTo(judgment);
    assertThat(ProposedRulesJson.law(ProposedRulesJson.write(judgment.law())))
        .isEqualTo(judgment.law());
    assertThat(ProposedRulesJson.report(ProposedRulesJson.write(report))).isEqualTo(report);
    assertThat(ProposedRulesJson.report(null)).isNull();
  }
}
