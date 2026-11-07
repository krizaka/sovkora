package com.krizaka.sovkora.tools.domain.model.law;

import java.util.List;
import java.util.UUID;

/**
 * The pull request a promotion opened.
 *
 * @param prUrl its address
 * @param branch its branch, never the base branch
 * @param rulesetPath the ruleset file it adds or revises
 * @param revision the ruleset revision
 * @param proposalIds the proposals it carries
 */
public record PromotionResult(
    String prUrl, String branch, String rulesetPath, int revision, List<UUID> proposalIds) {

  /** Copies the ids. */
  public PromotionResult {
    proposalIds = List.copyOf(proposalIds);
  }
}
