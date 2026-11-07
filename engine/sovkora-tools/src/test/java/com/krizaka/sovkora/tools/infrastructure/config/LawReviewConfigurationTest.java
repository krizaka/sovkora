package com.krizaka.sovkora.tools.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.application.port.law.*;
import com.krizaka.sovkora.tools.application.service.KindDemandService;
import com.krizaka.sovkora.tools.application.service.LawCorpusCatalogueService;
import com.krizaka.sovkora.tools.application.service.RuleProposalReviewService;
import com.krizaka.sovkora.tools.application.service.RuleProposalService;
import com.krizaka.sovkora.tools.application.service.RulesetPromotionService;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal;
import com.krizaka.sovkora.tools.domain.model.law.LawReviewRefusal.Code;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The review queue is wired with the corpus and works without derivation; promotion stays off until
 * the rulesets repository is configured with the GitHub App, and never with a personal token.
 */
class LawReviewConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              LawCorpusConfiguration.class,
              LawReviewConfiguration.class,
              RulesetPromotionConfiguration.class)
          .withBean(RuntimeConfigProvider.class, LawCorpusConfigurationTest.OffConfig::new)
          .withBean(LawCorpusStore.class, LawCorpusConfigurationTest.EmptyStore::new)
          .withBean(RuleProposalStore.class, () -> mock(RuleProposalStore.class))
          .withBean(KindDemandStore.class, () -> mock(KindDemandStore.class))
          .withBean(LawCorpusCatalogue.class, () -> mock(LawCorpusCatalogue.class));

  @Test
  void the_queue_is_wired_and_promotion_is_off_by_default() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(RuleProposalService.class);
          assertThat(context).hasSingleBean(RuleProposalReviewService.class);
          assertThat(context).hasSingleBean(KindDemandService.class);
          assertThat(context).hasSingleBean(LawCorpusCatalogueService.class);
          assertThat(context).doesNotHaveBean(RulesetPack.class);
          assertThat(context).doesNotHaveBean(RulesetPromotionPublisher.class);
          RulesetPromotionService promotion = context.getBean(RulesetPromotionService.class);
          assertThatThrownBy(() -> promotion.goldenCases("qc-visit-notice"))
              .isInstanceOfSatisfying(
                  LawReviewRefusal.class,
                  r -> assertThat(r.code()).isEqualTo(Code.LAW_PROMOTION_OFF));
        });
  }

  @Test
  void with_the_app_configured_the_pack_and_the_publisher_are_wired() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    String pem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder()
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
    runner
        .withPropertyValues(
            "orazaka.law.promotion.enabled=true",
            "orazaka.law.promotion.app-id=424242",
            "orazaka.law.promotion.installation-id=7",
            "orazaka.law.promotion.private-key=" + pem)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(RulesetPack.class);
              assertThat(context).hasSingleBean(RulesetPromotionPublisher.class);
              assertThat(context.getBean(RulesetPromotionProperties.class).toString())
                  .doesNotContain("PRIVATE KEY");
            });
  }

  @Test
  void promotion_on_without_its_installation_fails_at_start() {
    runner
        .withPropertyValues("orazaka.law.promotion.enabled=true", "orazaka.law.promotion.app-id=1")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void there_is_no_place_for_a_personal_token() {
    assertThat(
            Arrays.stream(RulesetPromotionProperties.class.getRecordComponents())
                .map(c -> c.getName().toLowerCase(java.util.Locale.ROOT)))
        .noneMatch(name -> name.contains("token"));
  }
}
