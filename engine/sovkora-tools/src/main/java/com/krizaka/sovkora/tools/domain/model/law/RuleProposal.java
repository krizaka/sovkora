package com.krizaka.sovkora.tools.domain.model.law;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of {@code rule_proposal}: a proposed rule, one revision of it, with its vet report and,
 * once reviewed, the decision (stage 5).
 *
 * @param id the row id
 * @param jurisdiction the jurisdiction, such as {@code QC}
 * @param kindId the kind of document the rule is for
 * @param ruleId the rule id
 * @param proposalRevision 1 for the first proposal of the rule, then one more per new proposal
 * @param rule the rule
 * @param derivationRunId the derivation that proposed it
 * @param status where it stands
 * @param vetReport what the vet found
 * @param reviewedBy who reviewed it, once reviewed
 * @param reviewedAt when, once reviewed
 * @param decisionNote the reviewer's note
 * @param packsPrUrl the pull request that promoted it
 * @param createdAt when it was written
 */
public record RuleProposal(
    UUID id,
    String jurisdiction,
    String kindId,
    String ruleId,
    int proposalRevision,
    ProposedRule rule,
    UUID derivationRunId,
    ProposalStatus status,
    VetReport vetReport,
    String reviewedBy,
    Instant reviewedAt,
    String decisionNote,
    String packsPrUrl,
    Instant createdAt) {

  /** Checks the parts every row has. */
  public RuleProposal {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(jurisdiction, "jurisdiction");
    Objects.requireNonNull(kindId, "kindId");
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(rule, "rule");
    Objects.requireNonNull(status, "status");
    if (proposalRevision < 1) {
      throw new IllegalArgumentException("proposalRevision starts at 1");
    }
  }
}
