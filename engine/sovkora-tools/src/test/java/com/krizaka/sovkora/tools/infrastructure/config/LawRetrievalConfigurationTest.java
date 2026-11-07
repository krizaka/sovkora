package com.krizaka.sovkora.tools.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.application.port.law.LawCorpusStore;
import com.krizaka.sovkora.tools.application.port.law.LawRetrievalCache;
import com.krizaka.sovkora.tools.application.service.LawRetrievalService;
import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleVersion;
import com.krizaka.sovkora.tools.domain.model.law.LawRetrievalRequest;
import com.krizaka.sovkora.tools.domain.model.law.LawRetrievalResult.Outcome;
import com.krizaka.sovkora.tools.domain.model.law.LawTargetRef;
import com.krizaka.sovkora.tools.domain.model.law.StoredRetrieval;
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

class LawRetrievalConfigurationTest {

  private final ApplicationContextRunner runner =
      runner(
          new LawCorpusConfigurationTest.OffConfig(),
          new LawCorpusConfigurationTest.EmptyStore(),
          new NoResearch());

  private static ApplicationContextRunner runner(
      RuntimeConfigProvider config, LawCorpusStore corpus, ResearchStore research) {
    return new ApplicationContextRunner()
        .withUserConfiguration(LawCorpusConfiguration.class, LawRetrievalConfiguration.class)
        .withBean(RuntimeConfigProvider.class, () -> config)
        .withBean(LawCorpusStore.class, () -> corpus)
        .withBean(ResearchStore.class, () -> research)
        .withBean(LawRetrievalCache.class, NoCache::new)
        .withBean(CitationVerifier.class, () -> (answer, mode, read) -> answer);
  }

  @Test
  void retrieval_is_not_wired_unless_the_property_says_so() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(LawRetrievalService.class);
        });
  }

  @Test
  void with_the_property_it_is_wired_and_still_off_while_the_settings_are() {
    runner
        .withPropertyValues("orazaka.law.retrieval.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              LawRetrievalService service = context.getBean(LawRetrievalService.class);
              assertThat(service.retrieve(request()).outcome()).isEqualTo(Outcome.DISABLED);
            });
  }

  @Test
  void with_everything_on_but_no_metering_wired_a_call_is_refused_before_any_model() {
    LawCorpusStore oneArticle =
        (LawCorpusStore)
            java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {LawCorpusStore.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "inForceOn" ->
                          Optional.of(
                              new LawArticleVersion(
                                  new LawArticle(
                                      "QC",
                                      "CCQ-1991",
                                      "1931",
                                      "fr",
                                      LocalDate.parse("2020-01-01"),
                                      "Un préavis de 24 heures.",
                                      "https://o.test/1931",
                                      "https://d.test"),
                                  Instant.EPOCH,
                                  Instant.EPOCH,
                                  null,
                                  UUID.randomUUID()));
                      case "hashCode" -> System.identityHashCode(proxy);
                      case "equals" -> proxy == args[0];
                      case "toString" -> "oneArticleCorpus";
                      default -> null;
                    });
    runner(new AllOn(), oneArticle, new OneSource())
        .withPropertyValues("orazaka.law.retrieval.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(LawRetrievalService.class).retrieve(request()).outcome())
                  .isEqualTo(Outcome.BUDGET_EXHAUSTED);
            });
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
    public void saveDocument(FetchedDocument document, String requestedUrl, UUID sourceId) {}

    @Override
    public void audit(FetchAudit audit) {}
  }

  private static final class AllOn implements RuntimeConfigProvider {
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return true;
    }

    @Override
    public int getInt(String key, int defaultValue) {
      return defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
      return defaultValue;
    }
  }

  private static final class OneSource implements ResearchStore {
    @Override
    public Optional<ResearchSource> source(String sourceKey) {
      return Optional.of(
          new ResearchSource(
              UUID.randomUUID(),
              "law-corpus",
              "OFFICIAL",
              "law-corpus",
              List.of(),
              List.of(),
              false,
              60,
              10,
              true));
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
    public void saveDocument(FetchedDocument document, String requestedUrl, UUID sourceId) {}

    @Override
    public void audit(FetchAudit audit) {}
  }

  private static final class NoCache implements LawRetrievalCache {
    @Override
    public Optional<StoredRetrieval> find(
        String jurisdiction, String kindId, String language, LocalDate bucket) {
      return Optional.empty();
    }

    @Override
    public void save(StoredRetrieval retrieval) {}
  }
}
