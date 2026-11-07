package com.krizaka.sovkora.tools.domain.model.law;

/**
 * Where a {@code rule_proposal} stands (ADR-089 §3 and §5). A proposal never becomes a ruleset
 * except through promotion (stage 5); this stage writes only the first two.
 */
public enum ProposalStatus {
  /** It passed {@code vet} and waits for a review. */
  PROPOSED,
  /** {@code vet} rejected it; its {@code vet_report} says why. */
  REJECTED_AUTO,
  /** A reviewer approved it (stage 5). */
  APPROVED,
  /** A reviewer rejected it (stage 5). */
  REJECTED,
  /** It is in a merged ruleset (stage 5). */
  PROMOTED,
  /** A later revision of the same rule replaced it before anyone reviewed it. */
  SUPERSEDED
}
