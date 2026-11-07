package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.tools.domain.model.law.RulesetPromotion;

/**
 * Opens the promotion of a ruleset for review: a pull request to sovkora-packs on a branch of its
 * own, never a push to the base branch (ADR-089 §5 step 4).
 */
public interface RulesetPromotionPublisher {

  /**
   * Where a promotion was opened.
   *
   * @param prUrl the pull request
   * @param branch its branch
   */
  record Opened(String prUrl, String branch) {}

  /**
   * Opens the pull request of one promotion.
   *
   * @param promotion the ruleset revision, its pinned articles and its provenance
   * @return the pull request and its branch
   */
  Opened open(RulesetPromotion promotion);
}
