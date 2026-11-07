package com.krizaka.sovkora.tools.infrastructure.law;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.research.Citation;
import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.core.domain.model.research.ClaimStatus;
import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import com.krizaka.sovkora.tools.application.port.law.GroundedClaimsModel;
import com.krizaka.sovkora.tools.application.service.CitationVerificationService;
import com.krizaka.sovkora.tools.application.service.GroundedResearchService;
import com.krizaka.sovkora.tools.application.service.LawCorpusResearchService;
import com.krizaka.sovkora.tools.domain.model.law.GroundedResearchRequest;
import com.krizaka.sovkora.tools.domain.model.law.GroundedResearchResult;
import com.krizaka.sovkora.tools.domain.model.law.GroundedResearchResult.Outcome;
import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawImportMode;
import com.krizaka.sovkora.tools.domain.model.law.LawImportStart;
import com.krizaka.sovkora.tools.domain.model.law.LawTargetRef;
import com.krizaka.sovkora.tools.domain.port.ResearchStore;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ResearchSourceEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.ResearchSourceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grounded research on PostgreSQL: the corpus and the research tables are the real ones, the
 * switches are read from the settings like an administrator's, and the model is scripted (no model
 * is paid for). The off-as-seeded state refuses, the switches turn it on, and the quote is checked
 * against the bytes the database holds.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(JpaLawCorpusStore.class)
class GroundedResearchIT extends AbstractContainerIntegrationTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @ComponentScan(
      basePackages = "com.krizaka.sovkora.tools.infrastructure.research",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*JpaResearchStore"))
  @EntityScan("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity")
  @EnableJpaRepositories("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository")
  static class Wiring {}

  private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
  private static final String TEXT =
      "Le locateur donne au locataire un préavis de visite de 24 heures.";

  @Autowired private JpaLawCorpusStore corpus;
  @Autowired private ResearchStore research;
  @Autowired private ResearchSourceRepository sources;
  @Autowired private JdbcTemplate jdbc;

  private final Map<String, String> settings = new HashMap<>();
  private String instrument;
  private int modelCalls;

  @BeforeEach
  void seed() {
    LawTestSchema.apply(jdbc);
    instrument = "G-" + UUID.randomUUID().toString().substring(0, 8);
    UUID importId =
        corpus.beginImport(
            new LawImportStart(
                "donnees-quebec",
                LawImportMode.AUTOMATED,
                "https://d.test/x.zip",
                null,
                null,
                NOW));
    corpus.add(
        importId,
        "donnees-quebec",
        new LawArticle(
            "QC",
            instrument,
            "1932",
            "fr",
            LocalDate.parse("2020-01-01"),
            TEXT,
            "https://www.legisquebec.gouv.qc.ca/fr/version/lc/" + instrument + "?code=se:1932",
            "https://d.test/x.zip"),
        NOW);
    if (research.source("law-corpus").isEmpty()) {
      ResearchSourceEntity row = new ResearchSourceEntity();
      row.setId(UUID.randomUUID());
      row.setSourceKey("law-corpus");
      row.setKind("OFFICIAL");
      row.setProvider("law-corpus");
      row.setAllowPatterns(new String[] {"www.legisquebec.gouv.qc.ca/**"});
      row.setDenyPatterns(new String[0]);
      row.setFreshnessSeconds(604_800);
      row.setRatePerMinute(30);
      row.setEnabled(true);
      sources.saveAndFlush(row);
    }
    settings.clear();
    modelCalls = 0;
  }

  private GroundedResearchService service() {
    RuntimeConfigProvider config =
        new RuntimeConfigProvider() {
          @Override
          public boolean getBoolean(String key, boolean defaultValue) {
            return Boolean.parseBoolean(settings.getOrDefault(key, String.valueOf(defaultValue)));
          }

          @Override
          public int getInt(String key, int defaultValue) {
            return Integer.parseInt(settings.getOrDefault(key, String.valueOf(defaultValue)));
          }

          @Override
          public String getString(String key, String defaultValue) {
            return settings.getOrDefault(key, defaultValue);
          }
        };
    GroundedClaimsModel model =
        (question, language, passages) -> {
          modelCalls++;
          GroundedClaimsModel.Passage p = passages.getFirst();
          return new GroundedAnswer(
              "",
              List.of(
                  new Claim(
                      "art. 1932 : préavis de visite de 24 heures",
                      ClaimStatus.TO_VALIDATE,
                      null,
                      List.of(
                          new Citation(
                              p.url(),
                              "préavis de visite de 24 heures",
                              p.contentHash(),
                              "2026-10-24T12:00:00Z")))),
              List.of());
        };
    return new GroundedResearchService(
        new RuntimeConfigGroundedResearchSettings(config),
        research,
        new LawCorpusResearchService(corpus, research, Clock.fixed(NOW, ZoneOffset.UTC)),
        model,
        new CitationVerificationService(research, config),
        (runId, actorId, providerKey) ->
            new com.krizaka.sovkora.tools.application.port.law.ResearchMeter.Hold() {
              @Override
              public void settle() {
                // nothing to settle in this test
              }

              @Override
              public void release(String reason) {
                // nothing to release in this test
              }
            },
        (runId, actorId) ->
            new com.krizaka.sovkora.tools.application.port.law.ChatTokenMeter.Hold() {
              @Override
              public long estimatedCredits() {
                return 0L;
              }

              @Override
              public void settle(com.krizaka.sovkora.core.domain.model.chat.TokenUsage usage) {
                // nothing to settle in this test
              }

              @Override
              public void release(String reason) {
                // nothing to release in this test
              }
            });
  }

  private GroundedResearchRequest request() {
    return new GroundedResearchRequest(
        List.of("law-corpus"),
        "QC",
        LocalDate.parse("2026-10-20"),
        "fr",
        "Quel préavis pour une visite ?",
        List.of(new LawTargetRef(instrument, List.of("1932"))),
        null,
        UUID.randomUUID(),
        "actor-1");
  }

  @Test
  @DisplayName("as seeded (every switch off) a call is refused and the model is never asked")
  void off_as_seeded() {
    assertThat(service().research(request()).outcome()).isEqualTo(Outcome.DISABLED);
    assertThat(modelCalls).isZero();
  }

  @Test
  @DisplayName("with the switches on, the claim is verified against the stored article")
  void answered_from_the_stored_article() {
    settings.put("research.enabled", "true");
    settings.put("law.corpus.enabled", "true");

    GroundedResearchResult result = service().research(request());

    assertThat(result.outcome()).isEqualTo(Outcome.ANSWERED);
    assertThat(result.answer().claims())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.status()).isEqualTo(ClaimStatus.VERIFIED);
              assertThat(c.citations().getFirst().url()).contains(instrument);
            });
    assertThat(result.sources()).hasSize(1);
  }

  @Test
  @DisplayName("a document is refused while law.lecture.enabled is off, even with research on")
  void lecture_stays_off() {
    settings.put("research.enabled", "true");
    settings.put("law.corpus.enabled", "true");
    GroundedResearchRequest base = request();
    GroundedResearchRequest withDocument =
        new GroundedResearchRequest(
            base.sources(),
            base.jurisdiction(),
            base.asOf(),
            base.language(),
            base.question(),
            base.targets(),
            "Un avis.",
            base.runId(),
            base.actorId());

    assertThat(service().research(withDocument).outcome()).isEqualTo(Outcome.LECTURE_DISABLED);
    assertThat(modelCalls).isZero();
  }
}
