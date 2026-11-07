package com.krizaka.sovkora.tools.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.application.port.law.LawCorpusStore;
import com.krizaka.sovkora.tools.application.port.law.LawRetrievalCache;
import com.krizaka.sovkora.tools.application.port.law.RuleDerivationModel;
import com.krizaka.sovkora.tools.application.port.law.RuleProposalStore;
import com.krizaka.sovkora.tools.application.service.KindDerivationService;
import com.krizaka.sovkora.tools.application.service.RuleDerivationService;
import com.krizaka.sovkora.tools.application.service.RuleProposalService;
import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.domain.model.law.RuleDerivationResult.Outcome;
import com.krizaka.sovkora.tools.domain.model.research.FetchAudit;
import com.krizaka.sovkora.tools.domain.model.research.FetchedDocument;
import com.krizaka.sovkora.tools.domain.model.research.ResearchSource;
import com.krizaka.sovkora.tools.domain.port.ResearchStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RuleDerivationConfigurationTest {

  private final ApplicationContextRunner runner = runner(LawCorpusConfigurationTest.OffConfig::new);

  private static ApplicationContextRunner runner(
      java.util.function.Supplier<RuntimeConfigProvider> config) {
    return new ApplicationContextRunner()
        .withUserConfiguration(
            LawCorpusConfiguration.class,
            LawRetrievalConfiguration.class,
            LawReviewConfiguration.class,
            RuleDerivationConfiguration.class)
        .withBean(RuntimeConfigProvider.class, config)
        .withBean(LawCorpusStore.class, LawCorpusConfigurationTest.EmptyStore::new)
        .withBean(ResearchStore.class, NoResearch::new)
        .withBean(LawRetrievalCache.class, NoCache::new)
        .withBean(RuleProposalStore.class, NoProposals::new)
        .withBean(
            com.krizaka.sovkora.tools.application.port.law.KindDemandStore.class,
            () ->
                org.mockito.Mockito.mock(
                    com.krizaka.sovkora.tools.application.port.law.KindDemandStore.class))
        .withBean(
            com.krizaka.sovkora.tools.application.port.law.LawCorpusCatalogue.class,
            () ->
                org.mockito.Mockito.mock(
                    com.krizaka.sovkora.tools.application.port.law.LawCorpusCatalogue.class))
        .withBean(CitationVerifier.class, () -> (answer, mode, read) -> answer);
  }

  @Test
  void derivation_is_not_wired_unless_the_property_says_so() {
    runner
        .withPropertyValues("orazaka.law.retrieval.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(RuleDerivationService.class);
              assertThat(context).doesNotHaveBean(KindDerivationService.class);
              // The review queue does not need derivation: its door is wired with the corpus.
              assertThat(context).hasSingleBean(RuleProposalService.class);
            });
  }

  @Test
  void with_the_property_it_is_wired_and_off_while_the_cap_is_not_set() {
    runner
        .withPropertyValues(
            "orazaka.law.retrieval.enabled=true", "orazaka.law.derivation.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(RuleProposalService.class);
              assertThat(context).hasSingleBean(KindDerivationService.class);
              assertThat(context.getBean(RuleDerivationService.class).derive(request()).outcome())
                  .isEqualTo(Outcome.DERIVATION_OFF);
            });
  }

  @Test
  void without_a_model_a_call_is_refused_and_never_answered_by_a_stand_in() {
    RuleDerivationModel unwired = new RuleDerivationConfiguration.UnwiredModel();
    RuleDerivationModel.Prompt prompt =
        new RuleDerivationModel.Prompt(
            "QC",
            "kind",
            "fr",
            LocalDate.parse("2026-10-20"),
            new RuleDerivationModel.Article("CCQ-1991", "1931", "fr", "a".repeat(64), "u", "t"),
            List.of(),
            30);

    assertThatThrownBy(unwired::callCeiling).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> unwired.propose(prompt)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void without_a_model_a_derivation_is_refused_before_the_retrieval_is_spent() {
    // A cap is set and the corpus is on, so only the missing model can stop it: were the
    // retrieval run first, these stubs would answer NOT_RETRIEVED instead of refusing.
    runner(CappedConfig::new)
        .withPropertyValues(
            "orazaka.law.retrieval.enabled=true", "orazaka.law.derivation.enabled=true")
        .run(
            context ->
                assertThatThrownBy(
                        () -> context.getBean(RuleDerivationService.class).derive(request()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no derivation model"));
  }

  /** The corpus and research on, a derivation cap set. */
  private static final class CappedConfig implements RuntimeConfigProvider {
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return true;
    }

    @Override
    public int getInt(String key, int defaultValue) {
      return "law.derive.max-credits".equals(key) ? 1_000 : defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
      return defaultValue;
    }
  }

  private static LawRetrievalRequest request() {
    return new LawRetrievalRequest(
        "QC",
        "kind",
        "q",
        "fr",
        LocalDate.parse("2026-10-20"),
        List.of(new LawTargetRef("CCQ-1991", List.of("1931"))),
        UUID.randomUUID(),
        "actor");
  }

  private static final class NoProposals implements RuleProposalStore {
    @Override
    public List<RuleProposal> record(
        String jurisdiction,
        String kindId,
        UUID derivationRunId,
        List<VettedProposal> proposals,
        Instant at) {
      throw new AssertionError("nothing is written while derivation is off");
    }

    @Override
    public Optional<RuleProposal> find(UUID id) {
      return Optional.empty();
    }

    @Override
    public List<RuleProposal> list(ProposalStatus status, String kindId) {
      return List.of();
    }

    @Override
    public Optional<RuleProposal> decide(
        UUID id,
        java.util.Set<ProposalStatus> from,
        ProposalStatus to,
        String reviewedBy,
        String note,
        Instant at) {
      return Optional.empty();
    }

    @Override
    public Optional<RuleProposal> revise(
        UUID previousId,
        java.util.Set<ProposalStatus> editable,
        VettedProposal edited,
        Instant at) {
      return Optional.empty();
    }

    @Override
    public String promote(List<UUID> ids, PromotionStep step) {
      throw new AssertionError("nothing is promoted here");
    }
  }

  private static final class NoResearch implements ResearchStore {
    @Override
    public Optional<ResearchSource> source(String sourceKey) {
      return Optional.empty();
    }

    @Override
    public Optional<FetchedDocument> freshDocument(UUID sourceId, String url, Instant notBefore) {
      return Optional.empty();
    }

    @Override
    public Optional<FetchedDocument> document(UUID sourceId, String url, String contentHash) {
      return Optional.empty();
    }

    @Override
    public void saveDocument(FetchedDocument document, String requestedUrl, UUID sourceId) {
      // intentionally empty: test double
    }

    @Override
    public void audit(FetchAudit audit) {
      // intentionally empty: test double
    }
  }

  private static final class NoCache implements LawRetrievalCache {
    @Override
    public Optional<StoredRetrieval> find(
        String jurisdiction, String kindId, String language, LocalDate bucket) {
      return Optional.empty();
    }

    @Override
    public void save(StoredRetrieval retrieval) {
      // intentionally empty: test double
    }
  }
}
