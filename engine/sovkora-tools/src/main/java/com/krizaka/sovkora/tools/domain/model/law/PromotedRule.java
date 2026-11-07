package com.krizaka.sovkora.tools.domain.model.law;

import java.util.List;
import java.util.Objects;

/**
 * A rule as it enters a ruleset: the approved proposal's rule with its law blocks pinned.
 *
 * @param rule the approved rule
 * @param law its law blocks, completed from the corpus
 */
public record PromotedRule(ProposedRule rule, List<PinnedLaw> law) {

  /** Copies the blocks. */
  public PromotedRule {
    Objects.requireNonNull(rule, "rule");
    law = List.copyOf(law);
  }
}
