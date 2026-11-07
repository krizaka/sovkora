package com.krizaka.sovkora.tools.domain.model.law;

import java.util.Objects;
import java.util.UUID;

/**
 * A derivation an administrator started (ADR-089 §8): its run id and what it wrote.
 *
 * @param runId the derivation run, which every proposal it wrote carries
 * @param result what was written and spent
 */
public record DerivationStarted(UUID runId, RuleDerivationResult result) {

  /** Checks the parts. */
  public DerivationStarted {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(result, "result");
  }

  /**
   * The proposals written for review.
   *
   * @return the {@code PROPOSED} count
   */
  public long proposed() {
    return result.proposals().stream().filter(p -> p.status() == ProposalStatus.PROPOSED).count();
  }

  /**
   * The proposals the vet rejected.
   *
   * @return the {@code REJECTED_AUTO} count
   */
  public long rejected() {
    return result.proposals().stream()
        .filter(p -> p.status() == ProposalStatus.REJECTED_AUTO)
        .count();
  }
}
