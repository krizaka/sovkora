package com.krizaka.sovkora.tools.domain.model.law;

import java.util.Objects;

/**
 * A proposed rule that went through {@link RuleProposalVet}, with its report. It is the only thing
 * {@code rule_proposal} accepts, and only the vet can make one: its constructor is not public, so
 * no caller can write a proposal that skipped the checks (rule LAW-003).
 */
public final class VettedProposal {

  private final ProposedRule rule;
  private final VetReport report;

  VettedProposal(ProposedRule rule, VetReport report) {
    this.rule = Objects.requireNonNull(rule, "rule");
    this.report = Objects.requireNonNull(report, "report");
  }

  /**
   * The rule as proposed.
   *
   * @return the rule
   */
  public ProposedRule rule() {
    return rule;
  }

  /**
   * What the vet found.
   *
   * @return the report
   */
  public VetReport report() {
    return report;
  }

  /**
   * The status the row is written with.
   *
   * @return {@code PROPOSED} when it passed, else {@code REJECTED_AUTO}
   */
  public ProposalStatus status() {
    return report.passed() ? ProposalStatus.PROPOSED : ProposalStatus.REJECTED_AUTO;
  }

  @Override
  public String toString() {
    return "VettedProposal[" + rule.ruleId() + ", " + status() + ", " + report.reasons() + "]";
  }
}
