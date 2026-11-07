package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.domain.model.chat.TokenUsage;
import com.krizaka.sovkora.core.domain.model.research.Citation;
import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.core.domain.model.research.ClaimReason;
import com.krizaka.sovkora.core.domain.model.research.ClaimStatus;
import com.krizaka.sovkora.core.domain.model.research.GroundedAnswer;
import com.krizaka.sovkora.core.domain.model.research.GroundedSource;
import com.krizaka.sovkora.core.domain.model.research.VerifyMode;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.application.port.law.ChatTokenMeter;
import com.krizaka.sovkora.tools.application.port.law.GroundedClaimsModel;
import com.krizaka.sovkora.tools.application.port.law.GroundedResearchSettings;
import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.domain.model.law.GroundedResearchRequest;
import com.krizaka.sovkora.tools.domain.model.law.GroundedResearchResult;
import com.krizaka.sovkora.tools.domain.model.law.GroundedResearchResult.Outcome;
import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawTargetRef;
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
 * The capability {@code research.grounded} (ADR-087 slice 5): answers only from what was read, each
 * claim cited, and refuses when nothing was read. The articles are test texts with no legal force;
 * the model is scripted, so no model is paid for.
 */
class GroundedResearchServiceTest {

  private static final String CCQ = "CCQ-1991";
  private static final LocalDate DAY = LocalDate.parse("2026-10-20");
  private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
  private static final UUID RUN = UUID.randomUUID();
  private static final String DOCUMENT =
      "AVIS. Le locateur visitera le logement demain à 10 h sans autre préavis.";

  private final InMemoryLawCorpus corpus = new InMemoryLawCorpus();
  private final InMemoryResearchStore research = new InMemoryResearchStore();
  private final FixedSettings settings = new FixedSettings();
  private final ScriptedModel model = new ScriptedModel();
  private final RecordingMeter meter = new RecordingMeter();
  private final RecordingTokenMeter tokens = new RecordingTokenMeter();
  private GroundedResearchService service;

  @BeforeEach
  void setUp() {
    research.declare("law-corpus", "law-corpus", true);
    UUID importId = UUID.randomUUID();
    add(
        importId,
        "1931",
        "Le locateur peut vérifier l'état du logement. Il ne peut restreindre la jouissance.");
    add(
        importId,
        "1932",
        "Le locateur donne au locataire un préavis de visite de 24 heures. "
            + "La visite se fait entre 9 h et 21 h.");
    service = serviceWith(settings);
  }

  private GroundedResearchService serviceWith(GroundedResearchSettings s) {
    return new GroundedResearchService(
        s,
        research,
        new LawCorpusResearchService(corpus, research, Clock.fixed(NOW, ZoneOffset.UTC)),
        model,
        new CitationVerificationService(research, new SeededConfig()),
        meter,
        tokens);
  }

  private void add(UUID importId, String ref, String text) {
    corpus.add(
        importId,
        "donnees-quebec",
        new LawArticle(
            "QC",
            CCQ,
            ref,
            "fr",
            LocalDate.parse("2020-01-01"),
            text,
            "https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:" + ref,
            "https://donneesquebec.test/ccq.zip"),
        NOW);
  }

  private static GroundedResearchRequest ask(String document, String... refs) {
    return new GroundedResearchRequest(
        List.of("law-corpus"),
        "QC",
        DAY,
        "fr",
        "Quel préavis pour une visite du logement ?",
        List.of(new LawTargetRef(CCQ, List.of(refs))),
        document,
        RUN,
        "actor-1");
  }

  @Test
  @DisplayName("every claim is cited and verified against the article; the answer is rendered")
  void the_answer_is_made_of_verified_claims() {
    GroundedResearchResult result = service.research(ask(null, "1931", "1932"));

    assertThat(result.outcome()).isEqualTo(Outcome.ANSWERED);
    assertThat(result.answer().claims()).hasSize(2);
    assertThat(result.answer().claims()).allMatch(c -> c.status() == ClaimStatus.VERIFIED);
    assertThat(result.answer().claims()).allMatch(c -> !c.citations().isEmpty());
    assertThat(result.answer().answer()).doesNotContain("[TO_VALIDATE]").contains("art. 1932");
    assertThat(result.sources()).extracting(GroundedSource::sourceKey).containsOnly("law-corpus");
    assertThat(meter.settled).isEqualTo(1);
  }

  @Test
  @DisplayName("the sources are the documents read, never the ones the model names")
  void sources_come_from_what_was_read() {
    model.namedSource = new GroundedSource("https://invented.test/law", "Invented", "web");

    GroundedResearchResult result = service.research(ask(null, "1932"));

    assertThat(result.sources())
        .extracting(GroundedSource::url)
        .noneMatch(u -> u.contains("invented"));
    assertThat(result.sources()).hasSize(1);
  }

  @Test
  @DisplayName("a quote the model invented stays TO_VALIDATE and is marked in the answer")
  void an_invented_quote_is_marked() {
    model.invent = true;

    GroundedResearchResult result = service.research(ask(null, "1932"));

    assertThat(result.answer().claims())
        .extracting(Claim::status)
        .containsOnly(ClaimStatus.TO_VALIDATE);
    assertThat(result.answer().answer()).startsWith("[TO_VALIDATE] ");
  }

  @Test
  @DisplayName("STRICT drops the claim it could not verify: the answer is empty")
  void strict_drops_unverified_claims() {
    model.invent = true;
    settings.mode = VerifyMode.STRICT;

    GroundedResearchResult result = service.research(ask(null, "1932"));

    assertThat(result.outcome()).isEqualTo(Outcome.ANSWERED);
    assertThat(result.answer().claims()).isEmpty();
    assertThat(result.answer().answer()).isEmpty();
  }

  @Test
  @DisplayName("no source in force: refused, nothing asked of the model, nothing held")
  void without_sources_it_refuses() {
    GroundedResearchResult result = service.research(ask(null, "9999"));

    assertThat(result.outcome()).isEqualTo(Outcome.NO_SOURCES);
    assertThat(result.answer().claims()).isEmpty();
    assertThat(result.unknown()).containsExactly("CCQ-1991 9999");
    assertThat(model.calls).isZero();
    assertThat(meter.holds).isZero();
  }

  @Test
  @DisplayName("off as seeded: no read, no model, no charge")
  void off_by_default() {
    FixedSettings off = new FixedSettings();
    off.research = false;
    off.corpus = false;

    GroundedResearchResult result = serviceWith(off).research(ask(null, "1932"));

    assertThat(result.outcome()).isEqualTo(Outcome.DISABLED);
    assertThat(model.calls).isZero();
    assertThat(meter.holds).isZero();
  }

  @Test
  @DisplayName("each switch alone keeps it off: research, the corpus, and the law-corpus source")
  void every_switch_is_needed() {
    for (int i = 0; i < 3; i++) {
      FixedSettings partial = new FixedSettings();
      partial.research = i != 0;
      partial.corpus = i != 1;
      research.declare("law-corpus", "law-corpus", i != 2);

      assertThat(serviceWith(partial).research(ask(null, "1932")).outcome())
          .isEqualTo(Outcome.DISABLED);
    }
    assertThat(model.calls).isZero();
  }

  @Test
  @DisplayName("a source other than law-corpus is refused, never guessed")
  void another_source_is_refused() {
    GroundedResearchRequest web =
        new GroundedResearchRequest(
            List.of("web"), "QC", DAY, "fr", "q", List.of(), null, RUN, "actor-1");

    assertThat(service.research(web).outcome()).isEqualTo(Outcome.UNSUPPORTED_SOURCE);
    assertThat(model.calls).isZero();
  }

  @Test
  @DisplayName("asOf is required: no day is read from a clock")
  void as_of_is_required() {
    GroundedResearchRequest noDay =
        new GroundedResearchRequest(
            List.of("law-corpus"),
            "QC",
            null,
            "fr",
            "q",
            List.of(new LawTargetRef(CCQ, List.of("1932"))),
            null,
            RUN,
            "actor-1");

    assertThatThrownBy(() -> service.research(noDay))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("asOf is required");
  }

  @Test
  @DisplayName("a run budget that cannot hold the call refuses it before the model")
  void the_budget_caps_the_call() {
    meter.refuse = true;

    GroundedResearchResult result = service.research(ask(null, "1932"));

    assertThat(result.outcome()).isEqualTo(Outcome.BUDGET_EXHAUSTED);
    assertThat(model.calls).isZero();
    assertThat(research.documents).isEmpty();
  }

  @Test
  @DisplayName("a model failure releases the hold and rethrows the original error")
  void a_model_failure_releases_the_hold() {
    model.failure = new IllegalStateException("model down");

    assertThatThrownBy(() -> service.research(ask(null, "1932")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("model down");
    assertThat(meter.released).isEqualTo(1);
    assertThat(meter.settled).isZero();
  }

  @Test
  @DisplayName("a document without law.lecture.enabled is refused before anything is read")
  void a_lecture_needs_its_switch() {
    GroundedResearchResult result = service.research(ask(DOCUMENT, "1932"));

    assertThat(result.outcome()).isEqualTo(Outcome.LECTURE_DISABLED);
    assertThat(model.calls).isZero();
    assertThat(meter.holds).isZero();
  }

  @Test
  @DisplayName("a lecture claim is verified only when the article AND the document are quoted")
  void a_lecture_pairs_an_article_with_the_document() {
    settings.lecture = true;
    model.documentQuote = "visitera le logement demain à 10 h sans autre préavis";

    GroundedResearchResult result = service.research(ask(DOCUMENT, "1932"));

    assertThat(result.outcome()).isEqualTo(Outcome.ANSWERED);
    assertThat(result.answer().claims()).hasSize(1);
    Claim claim = result.answer().claims().getFirst();
    assertThat(claim.status()).isEqualTo(ClaimStatus.VERIFIED);
    assertThat(claim.citations()).hasSize(2);
    // The document was handed to the model as data, and kept nowhere.
    assertThat(model.passages.getFirst()).anyMatch(p -> "document".equals(p.title()));
    assertThat(research.documents.values())
        .noneMatch(d -> d.text().contains("visitera le logement demain"));
  }

  @Test
  @DisplayName("a document quote that is not in the document leaves the claim TO_VALIDATE")
  void an_invented_document_quote_is_not_verified() {
    settings.lecture = true;
    model.documentQuote = "le locataire doit payer une pénalité";

    Claim claim = service.research(ask(DOCUMENT, "1932")).answer().claims().getFirst();

    assertThat(claim.status()).isEqualTo(ClaimStatus.TO_VALIDATE);
    assertThat(claim.reason()).isEqualTo(ClaimReason.UNSUPPORTED);
  }

  @Test
  @DisplayName("a lecture claim with no document quote at all is not verified")
  void a_claim_without_a_document_quote_is_not_verified() {
    settings.lecture = true;
    model.documentQuote = null;

    Claim claim = service.research(ask(DOCUMENT, "1932")).answer().claims().getFirst();

    assertThat(claim.status()).isEqualTo(ClaimStatus.TO_VALIDATE);
  }

  @Test
  @DisplayName("the chat tokens of the call are held, then settled once with what was reported")
  void the_tokens_are_billed_once() {
    GroundedResearchResult result = service.research(ask(null, "1932"));

    assertThat(result.outcome()).isEqualTo(Outcome.ANSWERED);
    assertThat(tokens.holds).isEqualTo(1);
    assertThat(tokens.settled).containsExactly(TokenUsage.reported(900, 100, 1_000));
    assertThat(tokens.released).isZero();
    assertThat(meter.settled).isEqualTo(1);
  }

  @Test
  @DisplayName("tokens the provider did not report are handed on as unreported, not as zero")
  void unreported_tokens_are_not_zero() {
    model.usage = TokenUsage.none();

    service.research(ask(null, "1932"));

    assertThat(tokens.settled).containsExactly(TokenUsage.none());
    assertThat(tokens.settled.getFirst().reported()).isFalse();
  }

  @Test
  @DisplayName(
      "a lecture whose reservations pass law.lecture.max-credits is refused, nothing charged")
  void a_lecture_stops_at_its_cap() {
    settings.lecture = true;
    settings.lectureCap = 39; // 10 for the call + 30 for the tokens

    GroundedResearchResult result = service.research(ask(DOCUMENT, "1932"));

    assertThat(result.outcome()).isEqualTo(Outcome.CREDIT_CAP_REACHED);
    assertThat(result.answer().claims()).isEmpty();
    assertThat(model.calls).isZero();
    assertThat(meter.settled).isZero();
    assertThat(meter.released).isEqualTo(1);
    assertThat(tokens.settled).isEmpty();
    assertThat(tokens.released).isEqualTo(1);
    assertThat(research.documents).isEmpty();
  }

  @Test
  @DisplayName("a lecture exactly at its cap runs, and settles once")
  void a_lecture_at_the_cap_runs() {
    settings.lecture = true;
    settings.lectureCap = 40;
    model.documentQuote = "visitera le logement demain à 10 h sans autre préavis";

    GroundedResearchResult result = service.research(ask(DOCUMENT, "1932"));

    assertThat(result.outcome()).isEqualTo(Outcome.ANSWERED);
    assertThat(meter.settled).isEqualTo(1);
    assertThat(tokens.settled).hasSize(1);
    assertThat(meter.released).isZero();
    assertThat(tokens.released).isZero();
  }

  @Test
  @DisplayName("a cap of 0 (or no row) keeps the lecture off: nothing is held")
  void a_zero_cap_is_no_lecture() {
    settings.lecture = true;
    settings.lectureCap = 0;

    GroundedResearchResult result = service.research(ask(DOCUMENT, "1932"));

    assertThat(result.outcome()).isEqualTo(Outcome.CREDIT_CAP_REACHED);
    assertThat(meter.holds).isZero();
    assertThat(tokens.holds).isZero();
    assertThat(model.calls).isZero();
  }

  @Test
  @DisplayName("the cap is for a lecture: a call with no document is not bound by it")
  void the_cap_is_not_for_a_plain_call() {
    settings.lectureCap = 0;

    assertThat(service.research(ask(null, "1932")).outcome()).isEqualTo(Outcome.ANSWERED);
  }

  @Test
  @DisplayName(
      "credits that cannot hold the tokens free the call's hold and refuse before the model")
  void tokens_that_cannot_be_held_free_the_call() {
    tokens.refuse = true;

    GroundedResearchResult result = service.research(ask(null, "1932"));

    assertThat(result.outcome()).isEqualTo(Outcome.BUDGET_EXHAUSTED);
    assertThat(meter.released).isEqualTo(1);
    assertThat(meter.settled).isZero();
    assertThat(model.calls).isZero();
  }

  @Test
  @DisplayName("a model failure releases both holds: nothing is settled")
  void a_failure_releases_both_holds() {
    model.failure = new IllegalStateException("model down");

    assertThatThrownBy(() -> service.research(ask(null, "1932")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(meter.released).isEqualTo(1);
    assertThat(tokens.released).isEqualTo(1);
    assertThat(tokens.settled).isEmpty();
  }

  /** A model that cites one claim per article, and the document when it is given. */
  private static final class ScriptedModel implements GroundedClaimsModel {
    int calls;
    boolean invent;
    String documentQuote;
    RuntimeException failure;
    GroundedSource namedSource;
    final List<List<Passage>> passages = new ArrayList<>();

    TokenUsage usage = TokenUsage.reported(900, 100, 1_000);

    @Override
    public Reply askMetered(String question, String language, List<Passage> given) {
      return new Reply(ask(question, language, given), usage);
    }

    @Override
    public GroundedAnswer ask(String question, String language, List<Passage> given) {
      calls++;
      passages.add(given);
      if (failure != null) {
        throw failure;
      }
      Passage document =
          given.stream().filter(p -> "document".equals(p.title())).findFirst().orElse(null);
      List<Claim> claims = new ArrayList<>();
      for (Passage p : given) {
        if (p == document) {
          continue;
        }
        String ref = p.title().substring(p.title().lastIndexOf(' ') + 1);
        String quote =
            invent ? "Le locateur peut entrer sans préavis." : p.text().split("(?<=\\.) ")[0];
        List<Citation> citations = new ArrayList<>();
        citations.add(new Citation(p.url(), quote, p.contentHash(), "2026-10-24T12:00:00Z"));
        if (document != null && documentQuote != null) {
          citations.add(
              new Citation(document.url(), documentQuote, document.contentHash(), "2026-10-24"));
        }
        claims.add(
            new Claim("art. " + ref + " : " + quote, ClaimStatus.TO_VALIDATE, null, citations));
      }
      List<GroundedSource> named = namedSource == null ? List.of() : List.of(namedSource);
      return new GroundedAnswer("", claims, named);
    }
  }

  /** A run budget on counters. */
  private static final class RecordingMeter implements ResearchMeter {
    int holds;
    int settled;
    int released;
    boolean refuse;
    long estimate = 10;

    @Override
    public Hold hold(String runId, String actorId, String providerKey) {
      if (refuse) {
        throw new BudgetExceededException("run budget spent");
      }
      holds++;
      return new Hold() {
        @Override
        public long estimatedCredits() {
          return estimate;
        }

        @Override
        public void settle() {
          settled++;
        }

        @Override
        public void release(String reason) {
          released++;
        }
      };
    }
  }

  /** The tokens of the model call, on counters. */
  private static final class RecordingTokenMeter implements ChatTokenMeter {
    int holds;
    final List<TokenUsage> settled = new ArrayList<>();
    int released;
    boolean refuse;
    long estimate = 30;

    @Override
    public Hold hold(String runId, String actorId) {
      if (refuse) {
        throw new ResearchMeter.BudgetExceededException("credits cannot hold the tokens");
      }
      holds++;
      return new Hold() {
        @Override
        public long estimatedCredits() {
          return estimate;
        }

        @Override
        public void settle(TokenUsage usage) {
          settled.add(usage);
        }

        @Override
        public void release(String reason) {
          released++;
        }
      };
    }
  }

  /** The settings as a test sets them; the seeded ones are research and corpus on here. */
  private static final class FixedSettings implements GroundedResearchSettings {
    boolean research = true;
    boolean corpus = true;
    boolean lecture;
    int lectureCap = 1_000;
    VerifyMode mode = VerifyMode.MARK;

    @Override
    public boolean researchEnabled() {
      return research;
    }

    @Override
    public boolean corpusEnabled() {
      return corpus;
    }

    @Override
    public boolean lectureEnabled() {
      return lecture;
    }

    @Override
    public int lectureMaxCredits() {
      return lectureCap;
    }

    @Override
    public VerifyMode verifyMode() {
      return mode;
    }

    @Override
    public int maxArticles() {
      return 40;
    }

    @Override
    public int quoteMaxChars() {
      return 300;
    }
  }

  /** Research settings as seeded. */
  private static final class SeededConfig implements RuntimeConfigProvider {
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
