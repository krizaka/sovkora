package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.domain.model.research.Citation;
import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.core.domain.model.research.ClaimStatus;
import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.application.port.law.GroundedClaimsModel;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.domain.model.law.LawRetrievalResult.Outcome;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stage 2 of ADR-089: the retrieval of the law for a kind, through grounded research on the {@code
 * law-corpus} source, and its cache. The articles are test texts written for this suite, with no
 * legal force; the model is a scripted port, so a test can count its calls.
 */
class LawRetrievalServiceTest {

  private static final String CCQ = "CCQ-1991";
  private static final String VISIT_KIND = "kind-visit-notice";
  private static final LocalDate NOTICE_DAY = LocalDate.parse("2026-10-20");
  private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
  private static final UUID RUN = UUID.randomUUID();

  private final InMemoryLawCorpus corpus = new InMemoryLawCorpus();
  private final InMemoryResearchStore research = new InMemoryResearchStore();
  private final InMemoryLawRetrievals cache = new InMemoryLawRetrievals();
  private final FixedLawSettings settings = new FixedLawSettings();
  private final ScriptedModel model = new ScriptedModel();
  private final RecordingMeter meter = new RecordingMeter();
  private LawRetrievalService service;

  @BeforeEach
  void setUp() {
    research.declare("law-corpus", "law-corpus", true);
    UUID importId = UUID.randomUUID();
    // The articles of the visit notice, and rent articles the notice must not bring in.
    add(
        importId,
        "1931",
        "Le locateur peut vérifier l'état du logement, y exécuter des travaux. "
            + "Il ne peut user de ce droit de manière à restreindre la jouissance du locataire.");
    add(
        importId,
        "1932",
        "Le locateur donne au locataire un préavis de visite de 24 heures. "
            + "La visite se fait entre 9 h et 21 h.");
    add(importId, "1933", "Le locataire ne peut refuser l'accès au logement sans motif sérieux.");
    add(
        importId,
        "1950",
        "Le locateur qui veut augmenter le loyer en avise le locataire par écrit.");
    add(importId, "1951", "L'avis d'augmentation du loyer indique le nouveau loyer proposé.");
    RuntimeConfigProvider config = new FixedConfig();
    CitationVerificationService verifier = new CitationVerificationService(research, config);
    LawCorpusResearchService provider =
        new LawCorpusResearchService(corpus, research, Clock.fixed(NOW, ZoneOffset.UTC));
    service =
        new LawRetrievalService(
            settings,
            research,
            provider,
            cache,
            model,
            verifier,
            meter,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void add(UUID importId, String ref, String text) {
    corpus.add(importId, "donnees-quebec", article(ref, text, LocalDate.parse("2020-01-01")), NOW);
  }

  private static LawArticle article(String ref, String text, LocalDate from) {
    return new LawArticle(
        "QC",
        CCQ,
        ref,
        "fr",
        from,
        text,
        "https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:" + ref,
        "https://donneesquebec.test/ccq.zip");
  }

  private static LawRetrievalRequest visitNotice() {
    return new LawRetrievalRequest(
        "QC",
        VISIT_KIND,
        "Quels articles régissent un avis de visite d'un logement au Québec ?",
        "fr",
        NOTICE_DAY,
        List.of(new LawTargetRef(CCQ, List.of("1931", "1932", "1933"))),
        RUN,
        "actor-1");
  }

  @Test
  @DisplayName("the golden case: a visit notice brings art. 1931 to 1933 and no rent article")
  void a_visit_notice_brings_the_visit_articles_and_no_rent_article() {
    LawRetrievalResult result = service.retrieve(visitNotice());

    assertThat(result.outcome()).isEqualTo(Outcome.RETRIEVED);
    assertThat(result.articles())
        .extracting(CitedArticle::ref)
        .containsExactly("1931", "1932", "1933");
    // The model was handed these three articles and nothing else.
    assertThat(model.passages).hasSize(1);
    assertThat(model.passages.getFirst())
        .extracting(GroundedClaimsModel.Passage::title)
        .containsExactly("CCQ-1991, art. 1931", "CCQ-1991, art. 1932", "CCQ-1991, art. 1933");
    // Every claim is verified by the ADR-087 verifier, against the corpus text, and cites only
    // them.
    assertThat(result.claims()).hasSize(3);
    assertThat(result.claims()).allMatch(c -> c.status() == ClaimStatus.VERIFIED);
    assertThat(result.claims())
        .flatExtracting(Claim::citations)
        .extracting(Citation::url)
        .containsExactly(
            "https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:1931",
            "https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:1932",
            "https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:1933");
    assertThat(result.claims())
        .noneMatch(c -> c.text().contains("1950") || c.text().contains("1951"));
  }

  @Test
  @DisplayName("a quote the model invented stays TO_VALIDATE: the verifier is the corpus text")
  void an_invented_quote_is_not_verified() {
    model.invent = true;

    LawRetrievalResult result = service.retrieve(visitNotice());

    assertThat(result.claims()).extracting(Claim::status).contains(ClaimStatus.TO_VALIDATE);
  }

  @Test
  @DisplayName("a cached retrieval makes no model call and no charge")
  void a_cached_retrieval_makes_no_model_call() {
    service.retrieve(visitNotice());
    assertThat(model.calls).isEqualTo(1);
    assertThat(meter.holds).isEqualTo(1);

    LawRetrievalResult again = service.retrieve(visitNotice());

    assertThat(again.outcome()).isEqualTo(Outcome.CACHED);
    assertThat(again.claims()).hasSize(3);
    assertThat(model.calls).isEqualTo(1);
    assertThat(meter.holds).isEqualTo(1);
  }

  @Test
  @DisplayName("a changed cited article marks the cache stale, and the next call recomputes")
  void a_changed_cited_article_makes_the_next_call_recompute() {
    service.retrieve(visitNotice());
    LawArticle old = corpus.history(CCQ, "1932", "fr").getFirst().article();
    LawArticle next =
        article(
            "1932",
            "Le locateur donne au locataire un préavis de 48 heures.",
            LocalDate.parse("2026-10-22"));
    corpus.replace(UUID.randomUUID(), old, next, NOW);
    cache.markStale(CCQ, "1932");
    assertThat(cache.rows.values()).allMatch(StoredRetrieval::stale);

    // The notice of 20 October is read at its own day: the old text is still the one in force.
    LawRetrievalResult sameDay = service.retrieve(visitNotice());
    assertThat(sameDay.outcome()).isEqualTo(Outcome.RETRIEVED);
    assertThat(model.calls).isEqualTo(2);
    assertThat(cache.rows.values()).noneMatch(StoredRetrieval::stale);

    // A notice of 25 October reads the new text, a new bucket: computed, then cached.
    LawRetrievalRequest later =
        new LawRetrievalRequest(
            "QC",
            VISIT_KIND,
            visitNotice().question(),
            "fr",
            LocalDate.parse("2026-10-25"),
            visitNotice().targets(),
            RUN,
            "actor-1");
    assertThat(service.retrieve(later).outcome()).isEqualTo(Outcome.RETRIEVED);
    assertThat(service.retrieve(later).outcome()).isEqualTo(Outcome.CACHED);
    assertThat(model.calls).isEqualTo(3);
  }

  @Test
  @DisplayName("a cached row that cites another text than the corpus holds is never served")
  void a_row_citing_another_version_is_recomputed_even_if_not_marked() {
    service.retrieve(visitNotice());
    LawArticle old = corpus.history(CCQ, "1933", "fr").getFirst().article();
    // The change reached the corpus but the stale mark was missed: the hashes still disagree.
    corpus.replace(
        UUID.randomUUID(),
        old,
        article("1933", "Le locataire peut refuser l'accès.", LocalDate.parse("2020-06-01")),
        NOW);

    assertThat(service.retrieve(visitNotice()).outcome()).isEqualTo(Outcome.RETRIEVED);
    assertThat(model.calls).isEqualTo(2);
  }

  @Test
  @DisplayName("a retrieval older than law.retrieval.max-age-days is redone")
  void an_old_retrieval_is_redone() {
    service.retrieve(visitNotice());
    settings.maxAgeDays = 7;
    LawRetrievalService later =
        new LawRetrievalService(
            settings,
            research,
            new LawCorpusResearchService(corpus, research, Clock.fixed(NOW, ZoneOffset.UTC)),
            cache,
            model,
            new CitationVerificationService(research, new FixedConfig()),
            meter,
            Clock.fixed(NOW.plusSeconds(8 * 86_400L), ZoneOffset.UTC));

    assertThat(later.retrieve(visitNotice()).outcome()).isEqualTo(Outcome.RETRIEVED);
    assertThat(model.calls).isEqualTo(2);
  }

  @Test
  @DisplayName("off by default: no read, no model, no charge")
  void off_by_default() {
    FixedLawSettings off = new FixedLawSettings();
    off.enabled = false;
    off.research = false;
    LawRetrievalService disabled = serviceWith(off);

    LawRetrievalResult result = disabled.retrieve(visitNotice());

    assertThat(result.outcome()).isEqualTo(Outcome.DISABLED);
    assertThat(result.claims()).isEmpty();
    assertThat(model.calls).isZero();
    assertThat(meter.holds).isZero();
    assertThat(cache.rows).isEmpty();
  }

  @Test
  @DisplayName("each switch alone keeps it off: the corpus, research, and the law-corpus source")
  void every_switch_is_needed() {
    for (int i = 0; i < 3; i++) {
      FixedLawSettings partial = new FixedLawSettings();
      partial.enabled = i != 0;
      partial.research = i != 1;
      research.declare("law-corpus", "law-corpus", i != 2);

      assertThat(serviceWith(partial).retrieve(visitNotice()).outcome())
          .isEqualTo(Outcome.DISABLED);
    }
    assertThat(model.calls).isZero();
    assertThat(meter.holds).isZero();
  }

  @Test
  @DisplayName("a day before the oldest text we hold is LAW_VERSION_UNKNOWN: nothing is asked")
  void before_the_oldest_version_nothing_is_asked() {
    LawRetrievalRequest early =
        new LawRetrievalRequest(
            "QC",
            VISIT_KIND,
            visitNotice().question(),
            "fr",
            LocalDate.parse("2019-01-01"),
            visitNotice().targets(),
            RUN,
            "actor-1");

    LawRetrievalResult result = service.retrieve(early);

    assertThat(result.outcome()).isEqualTo(Outcome.NO_ARTICLES);
    assertThat(result.unknown()).containsExactly("CCQ-1991 1931", "CCQ-1991 1932", "CCQ-1991 1933");
    assertThat(model.calls).isZero();
    assertThat(meter.holds).isZero();
  }

  @Test
  @DisplayName("an article with no version that day is reported, the others are still retrieved")
  void an_unknown_article_is_reported() {
    LawRetrievalRequest withUnknown =
        new LawRetrievalRequest(
            "QC",
            VISIT_KIND,
            visitNotice().question(),
            "fr",
            NOTICE_DAY,
            List.of(new LawTargetRef(CCQ, List.of("1931", "9999"))),
            RUN,
            "actor-1");

    LawRetrievalResult result = service.retrieve(withUnknown);

    assertThat(result.outcome()).isEqualTo(Outcome.RETRIEVED);
    assertThat(result.articles()).extracting(CitedArticle::ref).containsExactly("1931");
    assertThat(result.unknown()).containsExactly("CCQ-1991 9999");
  }

  @Test
  @DisplayName(
      "an article whose text no longer gives its recorded hash never reaches the model (#89)")
  void a_tampered_article_is_never_read_to_the_model() {
    corpus.rewriteText(CCQ, "1932", "fr", "Le locateur entre quand il veut.");

    LawRetrievalResult result = service.retrieve(visitNotice());

    assertThat(result.articles()).extracting(CitedArticle::ref).containsExactly("1931", "1933");
    assertThat(result.unknown()).containsExactly("CCQ-1991 1932");
    assertThat(model.passages.getFirst())
        .extracting(GroundedClaimsModel.Passage::text)
        .noneMatch(text -> text.contains("quand il veut"));
  }

  @Test
  @DisplayName("law.retrieval.max-articles bounds what one retrieval keeps")
  void the_articles_are_bounded() {
    settings.maxArticles = 2;

    LawRetrievalResult result = service.retrieve(visitNotice());

    assertThat(result.articles()).extracting(CitedArticle::ref).containsExactly("1931", "1932");
  }

  @Test
  @DisplayName("a grounded call is metered once as RESEARCH CALL on the provider and settled")
  void the_call_is_metered_and_settled() {
    service.retrieve(visitNotice());

    assertThat(meter.holds).isEqualTo(1);
    assertThat(meter.lastRun).isEqualTo(RUN.toString());
    assertThat(meter.lastActor).isEqualTo("actor-1");
    assertThat(meter.lastProvider).isEqualTo("law-corpus");
    assertThat(meter.settled).isEqualTo(1);
    assertThat(meter.released).isZero();
  }

  @Test
  @DisplayName(
      "a run budget that cannot hold the call refuses it before the model, and caches nothing")
  void the_run_budget_caps_the_call() {
    meter.refuse = true;

    LawRetrievalResult result = service.retrieve(visitNotice());

    assertThat(result.outcome()).isEqualTo(Outcome.BUDGET_EXHAUSTED);
    assertThat(model.calls).isZero();
    assertThat(cache.rows).isEmpty();
    assertThat(research.documents).isEmpty();
  }

  @Test
  @DisplayName("a model failure releases the hold and rethrows the original error")
  void a_model_failure_releases_the_hold() {
    model.failure = new IllegalStateException("model down");
    meter.releaseFails = true;

    assertThatThrownBy(() -> service.retrieve(visitNotice()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("model down")
        .hasSuppressedException(new IllegalStateException("ledger down"));
    assertThat(meter.released).isEqualTo(1);
    assertThat(meter.settled).isZero();
    assertThat(cache.rows).isEmpty();
  }

  @Test
  @DisplayName(
      "the language is part of the cache key: a French retrieval is never served for English")
  void a_french_retrieval_is_never_served_for_english() {
    UUID importId = UUID.randomUUID();
    for (String ref : List.of("1931", "1932", "1933")) {
      corpus.add(
          importId,
          "donnees-quebec",
          new LawArticle(
              "QC",
              CCQ,
              ref,
              "en",
              LocalDate.parse("2020-01-01"),
              "The lessor gives notice of " + ref + " hours. It is a test text.",
              "https://www.legisquebec.gouv.qc.ca/en/version/cs/CCQ-1991?code=se:" + ref,
              "https://donneesquebec.test/ccq.zip"),
          NOW);
    }
    LawRetrievalRequest english =
        new LawRetrievalRequest(
            "QC",
            VISIT_KIND,
            "Which articles govern a visit notice?",
            "en",
            NOTICE_DAY,
            visitNotice().targets(),
            RUN,
            "actor-1");

    assertThat(service.retrieve(visitNotice()).outcome()).isEqualTo(Outcome.RETRIEVED);
    LawRetrievalResult en = service.retrieve(english);

    assertThat(en.outcome()).isEqualTo(Outcome.RETRIEVED);
    assertThat(en.articles()).extracting(CitedArticle::language).containsOnly("en");
    assertThat(model.calls).isEqualTo(2);
    assertThat(service.retrieve(english).outcome()).isEqualTo(Outcome.CACHED);
    assertThat(service.retrieve(visitNotice()).outcome()).isEqualTo(Outcome.CACHED);
    assertThat(model.calls).isEqualTo(2);
    assertThat(cache.rows).hasSize(2);
  }

  @Test
  @DisplayName(
      "asOf is required, and the day asked is the day read: a Québec evening is not the UTC day")
  void the_day_asked_is_the_day_read() {
    // 23 October 21:30 in Québec is 24 October 01:30 UTC. A text takes effect on the 24th.
    Instant quebecEvening = Instant.parse("2026-10-24T01:30:00Z");
    LawArticle old = corpus.history(CCQ, "1932", "fr").getFirst().article();
    corpus.replace(
        UUID.randomUUID(),
        old,
        article("1932", "Le préavis de visite est de 48 heures.", LocalDate.parse("2026-10-24")),
        NOW);
    LawRetrievalService evening =
        new LawRetrievalService(
            settings,
            research,
            new LawCorpusResearchService(
                corpus, research, Clock.fixed(quebecEvening, ZoneOffset.UTC)),
            cache,
            model,
            new CitationVerificationService(research, new FixedConfig()),
            meter,
            Clock.fixed(quebecEvening, ZoneOffset.UTC));
    LawRetrievalRequest thatEvening =
        new LawRetrievalRequest(
            "QC",
            VISIT_KIND,
            visitNotice().question(),
            "fr",
            LocalDate.parse("2026-10-23"),
            visitNotice().targets(),
            RUN,
            "actor-1");

    LawRetrievalResult result = evening.retrieve(thatEvening);

    // The 23rd reads the text of the 23rd, not the one that takes effect on the 24th.
    assertThat(model.passages.getFirst())
        .filteredOn(p -> p.title().endsWith("1932"))
        .extracting(GroundedClaimsModel.Passage::text)
        .containsExactly(old.text());
    assertThat(result.outcome()).isEqualTo(Outcome.RETRIEVED);
    assertThatThrownBy(
            () ->
                new LawRetrievalRequest(
                    "QC", VISIT_KIND, "q", "fr", null, visitNotice().targets(), RUN, "actor-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("asOf is required");
  }

  @Test
  void the_language_is_fr_or_en() {
    LawRetrievalRequest odd =
        new LawRetrievalRequest(
            "QC", VISIT_KIND, "q", "de", NOTICE_DAY, visitNotice().targets(), RUN, "actor-1");

    assertThatThrownBy(() -> service.retrieve(odd)).isInstanceOf(IllegalArgumentException.class);
  }

  private LawRetrievalService serviceWith(FixedLawSettings s) {
    return new LawRetrievalService(
        s,
        research,
        new LawCorpusResearchService(corpus, research, Clock.fixed(NOW, ZoneOffset.UTC)),
        cache,
        model,
        new CitationVerificationService(research, new FixedConfig()),
        meter,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** A model that answers one verified-looking claim per passage, and counts its calls. */
  private static final class ScriptedModel implements GroundedClaimsModel {
    int calls;
    boolean invent;
    RuntimeException failure;
    final List<List<Passage>> passages = new ArrayList<>();

    @Override
    public GroundedAnswer ask(String question, String language, List<Passage> given) {
      calls++;
      passages.add(given);
      if (failure != null) {
        throw failure;
      }
      List<Claim> claims = new ArrayList<>();
      for (Passage p : given) {
        String ref = p.title().substring(p.title().lastIndexOf(' ') + 1);
        String quote =
            invent ? "Le locateur peut entrer sans préavis." : p.text().split("(?<=\\.) ")[0];
        claims.add(
            new Claim(
                "art. " + ref + " : " + quote,
                ClaimStatus.TO_VALIDATE,
                null,
                List.of(new Citation(p.url(), quote, p.contentHash(), "2026-10-24T12:00:00Z"))));
      }
      return new GroundedAnswer("", claims, List.of());
    }
  }

  /** A run budget on counters. */
  private static final class RecordingMeter implements ResearchMeter {
    int holds;
    int settled;
    int released;
    boolean refuse;
    boolean releaseFails;
    String lastRun;
    String lastActor;
    String lastProvider;

    @Override
    public Hold hold(String runId, String actorId, String providerKey) {
      if (refuse) {
        throw new BudgetExceededException("run budget spent");
      }
      holds++;
      lastRun = runId;
      lastActor = actorId;
      lastProvider = providerKey;
      return new Hold() {
        @Override
        public void settle() {
          settled++;
        }

        @Override
        public void release(String reason) {
          released++;
          if (releaseFails) {
            throw new IllegalStateException("ledger down");
          }
        }
      };
    }
  }

  /** Research settings as seeded: only the quote limit is set. */
  private static final class FixedConfig implements RuntimeConfigProvider {
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return defaultValue;
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
}
