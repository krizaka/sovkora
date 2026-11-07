package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.tools.application.port.law.ResearchMeter;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleQuery;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleRead;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleRead.Outcome;
import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import com.krizaka.sovkora.tools.domain.model.law.QuoteCheck;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The read API's use case (issue #89, ADR-089 §2): {@code asOf} required, the article's hash
 * checked on every read (fail closed), an optional quote checked against the text, and one {@code
 * CALL} metered — charged only when the text is served.
 */
class LawArticleReadServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), ZoneOffset.UTC);
  private static final String ACTOR = "3f0c6a59-5f0b-4a43-9a52-8f3f0d6b7c11";

  private InMemoryLawCorpus store;
  private FixedLawSettings settings;
  private RecordingMeter meter;
  private LawArticleReadService reads;

  @BeforeEach
  void setUp() {
    store = new InMemoryLawCorpus();
    settings = new FixedLawSettings();
    settings.instruments = List.of(LawTarget.wholeInstrument("QC", "CCQ-1991"));
    FixtureLawSource source = new FixtureLawSource();
    LawWatchService watcher =
        new LawWatchService(
            List.of(source), settings, new LawImportService(store, settings, null, CLOCK));
    source.serve("/law/CCQ-1991_FR-slice.xml");
    watcher.run();
    meter = new RecordingMeter();
    reads = service(meter);
  }

  private LawArticleReadService service(ResearchMeter withMeter) {
    return new LawArticleReadService(
        new LawStatuteLookupService(store, settings, CLOCK), settings, withMeter);
  }

  private static LawArticleQuery query(String ref, String asOf, String quote) {
    return new LawArticleQuery("QC", "CCQ-1991", ref, "fr", asOf, quote);
  }

  // ── asOf is required ─────────────────────────────────────────────────────────────────────

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {"", " ", "today", "2026-13-01", "2026-1-1", "25/06/2025", "2025-06-01T00:00"})
  void a_missing_or_unreadable_day_is_refused_before_anything_is_held(String asOf) {
    assertThatThrownBy(() -> reads.read(query("1942", asOf, null), ACTOR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("asOf");
    assertThat(meter.holds).isEmpty();
  }

  @Test
  void a_day_is_never_read_from_the_clock() {
    // The lookup would read today (2026-10-23) from its clock; the API never lets it.
    assertThatThrownBy(() -> reads.read(query("1942", null, null), ACTOR))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── the rest of the question ─────────────────────────────────────────────────────────────

  @Test
  void a_blank_part_a_language_other_than_fr_or_en_or_a_blank_quote_is_refused_unheld() {
    List<LawArticleQuery> invalid =
        List.of(
            new LawArticleQuery(" ", "CCQ-1991", "1942", "fr", "2025-06-01", null),
            new LawArticleQuery("QC", null, "1942", "fr", "2025-06-01", null),
            new LawArticleQuery("QC", "CCQ-1991", "", "fr", "2025-06-01", null),
            new LawArticleQuery("QC", "CCQ-1991", "1942", "de", "2025-06-01", null),
            new LawArticleQuery("QC", "CCQ-1991", "1942", null, "2025-06-01", null),
            new LawArticleQuery("QC", "CCQ-1991", "1942", "fr", "2025-06-01", " \n "));
    for (LawArticleQuery q : invalid) {
      assertThatThrownBy(() -> reads.read(q, ACTOR))
          .as(q.toString())
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> reads.read(query("1942", "2025-06-01", null), " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(meter.holds).isEmpty();
  }

  // ── found: served and charged once ───────────────────────────────────────────────────────

  @Test
  void the_version_in_force_is_served_and_one_call_is_charged() {
    LawArticleRead read = reads.read(query("1942", "2025-06-01", null), ACTOR);

    assertThat(read.outcome()).isEqualTo(Outcome.FOUND);
    assertThat(read.version().text()).contains("trois mois");
    assertThat(read.version().contentHash()).matches("[0-9a-f]{64}");
    assertThat(read.version().inForceFrom()).isEqualTo(LocalDate.of(1999, 4, 1));
    assertThat(read.version().officialUrl())
        .isEqualTo("https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:1942");
    assertThat(read.version().importId()).isIn(store.imports.keySet());
    assertThat(read.quote()).isNull();

    assertThat(meter.holds).hasSize(1);
    RecordingMeter.Held held = meter.holds.getFirst();
    assertThat(held.providerKey).isEqualTo("law-corpus");
    assertThat(held.actorId).isEqualTo(ACTOR);
    assertThat(held.runId).isEqualTo(read.readId());
    assertThat(held.settled).isEqualTo(1);
    assertThat(held.released).isEmpty();
  }

  @Test
  void a_quote_in_the_article_is_verified_one_that_is_not_is_said_so_both_are_charged() {
    LawArticleRead in = reads.read(query("1942", "2025-06-01", "  trois\nmois "), ACTOR);
    LawArticleRead out = reads.read(query("1942", "2025-06-01", "quatre mois"), ACTOR);

    assertThat(in.quote()).isEqualTo(QuoteCheck.VERIFIED);
    assertThat(out.outcome()).isEqualTo(Outcome.FOUND);
    assertThat(out.quote()).isEqualTo(QuoteCheck.NOT_IN_ARTICLE);
    assertThat(meter.holds).extracting(h -> h.settled).containsExactly(1, 1);
  }

  // ── not found: nothing charged ───────────────────────────────────────────────────────────

  @Test
  void an_unknown_version_is_released_never_charged() {
    LawArticleRead read = reads.read(query("1942", "1990-01-01", null), ACTOR);

    assertThat(read.outcome()).isEqualTo(Outcome.LAW_VERSION_UNKNOWN);
    assertThat(read.version()).isNull();
    assertThat(meter.holds).hasSize(1);
    assertThat(meter.holds.getFirst().settled).isZero();
    assertThat(meter.holds.getFirst().released).containsExactly("LAW_VERSION_UNKNOWN");
  }

  @Test
  void a_switched_off_corpus_answers_disabled_and_holds_nothing() {
    settings.enabled = false;

    LawArticleRead read = reads.read(query("1942", "2025-06-01", null), ACTOR);

    assertThat(read.outcome()).isEqualTo(Outcome.LAW_CORPUS_DISABLED);
    assertThat(read.version()).isNull();
    assertThat(meter.holds).isEmpty();
  }

  @Test
  void a_hash_mismatch_fails_closed_no_text_nothing_charged() {
    store.rewriteText("CCQ-1991", "1942", "fr", "Le bail est d'un an.");

    LawArticleRead read = reads.read(query("1942", "2025-06-01", "un an"), ACTOR);

    assertThat(read.outcome()).isEqualTo(Outcome.LAW_INTEGRITY_ERROR);
    assertThat(read.version()).isNull();
    assertThat(read.quote()).isNull();
    assertThat(meter.holds.getFirst().settled).isZero();
    assertThat(meter.holds.getFirst().released).containsExactly("LAW_INTEGRITY_ERROR");
  }

  @Test
  void a_failed_read_releases_the_hold_and_the_failure_surfaces() {
    store.readFailure = new IllegalStateException("database down");

    assertThatThrownBy(() -> reads.read(query("1942", "2025-06-01", null), ACTOR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("database down");
    assertThat(meter.holds.getFirst().settled).isZero();
    assertThat(meter.holds.getFirst().released).hasSize(1);
  }

  // ── the meter: fail closed ───────────────────────────────────────────────────────────────

  @Test
  void with_no_meter_wired_the_read_is_refused_never_served_unmetered() {
    LawArticleRead read = service(null).read(query("1942", "2025-06-01", null), ACTOR);

    assertThat(read.outcome()).isEqualTo(Outcome.LAW_METERING_UNAVAILABLE);
    assertThat(read.version()).isNull();
  }

  @Test
  void a_budget_that_cannot_hold_the_call_refuses_the_read() {
    meter.refuse = true;

    LawArticleRead read = reads.read(query("1942", "2025-06-01", null), ACTOR);

    assertThat(read.outcome()).isEqualTo(Outcome.LAW_BUDGET_EXCEEDED);
    assertThat(read.version()).isNull();
  }

  /** A meter that records its holds. */
  static final class RecordingMeter implements ResearchMeter {

    static final class Held implements Hold {
      final String runId;
      final String actorId;
      final String providerKey;
      int settled;
      final List<String> released = new ArrayList<>();

      Held(String runId, String actorId, String providerKey) {
        this.runId = runId;
        this.actorId = actorId;
        this.providerKey = providerKey;
      }

      @Override
      public void settle() {
        settled++;
      }

      @Override
      public void release(String reason) {
        released.add(reason);
      }
    }

    final List<Held> holds = new ArrayList<>();
    boolean refuse;

    @Override
    public Hold hold(String runId, String actorId, String providerKey) {
      if (refuse) {
        throw new BudgetExceededException("no credits left");
      }
      Held held = new Held(runId, actorId, providerKey);
      holds.add(held);
      return held;
    }
  }
}
