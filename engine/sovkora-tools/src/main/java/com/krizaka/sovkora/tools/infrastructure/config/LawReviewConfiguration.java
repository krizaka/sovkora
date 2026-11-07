package com.krizaka.sovkora.tools.infrastructure.config;

import com.krizaka.sovkora.tools.application.port.law.*;
import com.krizaka.sovkora.tools.application.service.*;
import com.krizaka.sovkora.tools.domain.model.law.GoldenCase;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal;
import com.krizaka.sovkora.tools.domain.model.law.PackKind;
import com.krizaka.sovkora.tools.domain.model.law.RulesetPromotion;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the review queue of ADR-089 (§5, §8) on top of {@link LawCorpusConfiguration}: the queue
 * and its decisions, the edit vetted again, the corpus and demand tabs, and the promotion. The
 * queue reads and writes only {@code rule_proposal}; it works whether or not derivation is on.
 * Promotion is off until {@link RulesetPromotionConfiguration} wires the rulesets repository: then
 * a promotion is refused as {@code LAW_PROMOTION_OFF}, never opened another way.
 */
@Configuration(proxyBeanMethods = false)
public class LawReviewConfiguration {

  @Bean
  RuleProposalService ruleProposalService(RuleProposalStore store) {
    return new RuleProposalService(store, Clock.systemUTC());
  }

  @Bean
  RuleProposalReviewService ruleProposalReviewService(
      RuleProposalService proposals, LawStatuteLookupService lookups, LawSettings settings) {
    return new RuleProposalReviewService(proposals, lookups, settings);
  }

  @Bean
  KindDemandService kindDemandService(KindDemandStore store) {
    return new KindDemandService(store);
  }

  @Bean
  LawCorpusCatalogueService lawCorpusCatalogueService(LawCorpusCatalogue catalogue) {
    return new LawCorpusCatalogueService(catalogue);
  }

  @Bean
  RulesetPromotionService rulesetPromotionService(
      RuleProposalService proposals,
      LawStatuteLookupService lookups,
      LawSettings settings,
      ObjectProvider<RulesetPack> pack,
      ObjectProvider<RulesetPromotionPublisher> publisher) {
    return new RulesetPromotionService(
        proposals,
        lookups,
        settings,
        pack.getIfAvailable(UnwiredPack::new),
        publisher.getIfAvailable(() -> UnwiredPack::refuse));
  }

  /** No rulesets repository: every read and every promotion is refused as off. */
  static final class UnwiredPack implements RulesetPack {

    @Override
    public Optional<PackKind> kind(String kindId) {
      throw off();
    }

    @Override
    public List<GoldenCase> goldenCases(String kindId) {
      throw off();
    }

    @Override
    public OptionalInt rulesetRevision(String kindId, LocalDate effectiveFrom) {
      throw off();
    }

    static RulesetPromotionPublisher.Opened refuse(RulesetPromotion promotion) {
      throw off();
    }

    private static LawReviewRefusal off() {
      return new LawReviewRefusal(
          LawReviewRefusal.Code.LAW_PROMOTION_OFF,
          "the rulesets repository is not configured (orazaka.law.promotion.enabled)");
    }
  }
}
