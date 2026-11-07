package com.krizaka.sovkora.tools.domain.model.law;

import java.util.List;

/**
 * A rule as the derivation model proposes it, in the shape of a ruleset rule (ADR-089 §3): what a
 * promotion would copy into {@code rulesets/<kind>/<effectiveFrom>.json}. Nothing here is trusted
 * until {@link RuleProposalVet} has read it.
 *
 * @param ruleId the rule id
 * @param title what the rule checks
 * @param severity {@code BLOCKING}, {@code MAJOR} or {@code ADVISORY}
 * @param method {@code DETERMINISTIC} or {@code JUDGMENT}
 * @param subject the pattern of what the rule is about; a PASS quotes a passage of it (#127)
 * @param check the deterministic check; absent for a {@code JUDGMENT} rule
 * @param question the question a {@code JUDGMENT} rule asks; absent otherwise
 * @param citation the article in words, as a finding shows it
 * @param sourceUrl the official address of the article
 * @param onPass what a PASS says
 * @param onFail what a FAIL says
 * @param fixtures the fixture pair
 * @param law the articles it rests on, at least one
 */
public record ProposedRule(
    String ruleId,
    String title,
    String severity,
    String method,
    String subject,
    RuleCheck check,
    String question,
    String citation,
    String sourceUrl,
    String onPass,
    String onFail,
    RuleFixtures fixtures,
    List<LawBlock> law) {

  /** Copies the list; a missing one is empty. */
  public ProposedRule {
    law = law == null ? List.of() : List.copyOf(law);
  }
}
