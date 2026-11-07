package com.krizaka.sovkora.tools.architecture.fixture;

import com.krizaka.sovkora.tools.application.port.law.RulesetPromotionPublisher;
import com.krizaka.sovkora.tools.application.service.RuleProposalService;
import com.krizaka.sovkora.tools.domain.model.law.RulesetPromotion;
import com.krizaka.sovkora.tools.domain.model.law.VettedProposal;
import java.util.List;
import java.util.UUID;

/**
 * Planted violations of [LAW-005] and [LAW-006]: a pull request opened without the promotion's
 * checks, and an edit recorded without the vet. Never used; it proves the rules fail.
 */
public final class PromotionThatSkipsTheGate {

  private PromotionThatSkipsTheGate() {}

  public static String open(RulesetPromotionPublisher publisher, RulesetPromotion promotion) {
    return publisher.open(promotion).prUrl();
  }

  public static String promote(RuleProposalService proposals, List<UUID> ids) {
    return proposals.promote(ids, locked -> "https://example.test/pull/1");
  }

  public static void edit(RuleProposalService proposals, UUID id, VettedProposal unvetted) {
    proposals.revise(id, unvetted);
  }
}
