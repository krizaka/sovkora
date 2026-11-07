package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import com.krizaka.sovkora.tools.domain.model.law.StatuteLookup;
import com.krizaka.sovkora.tools.domain.model.law.StatuteLookup.Outcome;
import com.krizaka.sovkora.tools.domain.model.law.StatuteVersion;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code lookupStatute} with a point in time, on a corpus built by the importers from recorded XML:
 * article 1942 as first read (1999), amended (2026-10-01), then reverted (2026-10-15).
 */
class LawStatuteLookupServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), ZoneOffset.UTC);

  private InMemoryLawCorpus store;
  private FixedLawSettings settings;
  private LawStatuteLookupService lookups;

  @BeforeEach
  void setUp() {
    store = new InMemoryLawCorpus();
    settings = new FixedLawSettings();
    settings.instruments = List.of(LawTarget.wholeInstrument("QC", "CCQ-1991"));
    FixtureLawSource source = new FixtureLawSource();
    LawWatchService watcher =
        new LawWatchService(
            List.of(source), settings, new LawImportService(store, settings, null, CLOCK));
    for (String resource :
        List.of(
            "/law/CCQ-1991_FR-slice.xml",
            "/law/CCQ-1991_FR-slice-amended.xml",
            "/law/CCQ-1991_FR-slice-reverted.xml")) {
      source.serve(resource);
      watcher.run();
    }
    lookups = new LawStatuteLookupService(store, settings, CLOCK);
  }

  private StatuteLookup at(String ref, String language, LocalDate asOf) {
    return lookups.lookupStatute("QC", "CCQ-1991", ref, language, asOf);
  }

  @Test
  void a_past_day_returns_the_text_that_was_in_force_then() {
    StatuteLookup lookup = at("1942", "fr", LocalDate.of(2025, 6, 1));

    assertThat(lookup.outcome()).isEqualTo(Outcome.FOUND);
    StatuteVersion version = lookup.version();
    assertThat(version.text()).contains("trois mois").doesNotContain("quatre mois");
    assertThat(version.inForceFrom()).isEqualTo(LocalDate.of(1999, 4, 1));
    assertThat(version.contentHash()).matches("[0-9a-f]{64}");
    assertThat(version.officialUrl())
        .isEqualTo("https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:1942");
    assertThat(version.importId()).isNotNull().isIn(store.imports.keySet());
  }

  @Test
  void the_day_an_amendment_takes_effect_is_the_first_day_of_the_new_text() {
    assertThat(at("1942", "fr", LocalDate.of(2026, 9, 30)).version().text()).contains("trois mois");
    StatuteVersion amended = at("1942", "fr", LocalDate.of(2026, 10, 1)).version();
    assertThat(amended.text()).contains("quatre mois");
    assertThat(amended.inForceFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
  }

  @Test
  void a_reverted_text_is_in_force_from_its_own_new_date() {
    assertThat(at("1942", "fr", LocalDate.of(2026, 10, 14)).version().text())
        .contains("quatre mois");
    StatuteVersion reverted = at("1942", "fr", LocalDate.of(2026, 10, 15)).version();

    assertThat(reverted.text()).contains("trois mois");
    assertThat(reverted.inForceFrom()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(reverted.contentHash())
        .isEqualTo(at("1942", "fr", LocalDate.of(2025, 6, 1)).version().contentHash());
  }

  @Test
  void a_day_before_the_oldest_version_we_hold_is_unknown_never_read_from_a_later_text() {
    // 1943 took effect on 2024-12-26: we hold nothing older.
    assertThat(at("1943", "fr", LocalDate.of(2024, 12, 25)).outcome())
        .isEqualTo(Outcome.LAW_VERSION_UNKNOWN);
    assertThat(at("1943", "fr", LocalDate.of(2024, 12, 26)).outcome()).isEqualTo(Outcome.FOUND);
  }

  @Test
  void an_unknown_article_or_language_is_unknown() {
    assertThat(at("9999", "fr", LocalDate.of(2026, 1, 1)).outcome())
        .isEqualTo(Outcome.LAW_VERSION_UNKNOWN);
    assertThat(at("1942", "en", LocalDate.of(2026, 1, 1)).outcome())
        .isEqualTo(Outcome.LAW_VERSION_UNKNOWN);
    assertThat(lookups.lookupStatute("QC", "T-0.1", "16", "fr", LocalDate.of(2026, 1, 1)).outcome())
        .isEqualTo(Outcome.LAW_VERSION_UNKNOWN);
    assertThat(
            lookups
                .lookupStatute("CA", "CCQ-1991", "1942", "fr", LocalDate.of(2026, 1, 1))
                .outcome())
        .isEqualTo(Outcome.LAW_VERSION_UNKNOWN);
  }

  @Test
  void a_removed_article_is_known_before_its_removal_and_unknown_after() {
    InMemoryLawCorpus removedStore = new InMemoryLawCorpus();
    FixtureLawSource source = new FixtureLawSource();
    LawWatchService watcher =
        new LawWatchService(
            List.of(source), settings, new LawImportService(removedStore, settings, null, CLOCK));
    source.serve("/law/CCQ-1991_FR-slice.xml");
    watcher.run();
    source.serve("/law/CCQ-1991_FR-slice-1942-removed.xml");
    watcher.run();
    LawStatuteLookupService afterRemoval =
        new LawStatuteLookupService(removedStore, settings, CLOCK);

    assertThat(
            afterRemoval
                .lookupStatute("QC", "CCQ-1991", "1942", "fr", LocalDate.of(2026, 10, 22))
                .outcome())
        .isEqualTo(Outcome.FOUND);
    assertThat(
            afterRemoval
                .lookupStatute("QC", "CCQ-1991", "1942", "fr", LocalDate.of(2026, 10, 23))
                .outcome())
        .isEqualTo(Outcome.LAW_VERSION_UNKNOWN);
  }

  @Test
  void no_day_means_today() {
    // Today (2026-10-23): the text restored on 2026-10-15.
    StatuteVersion today = lookups.lookupStatute("QC", "CCQ-1991", "1942", "fr", null).version();
    assertThat(today.text()).contains("trois mois");
    assertThat(today.inForceFrom()).isEqualTo(LocalDate.of(2026, 10, 15));
  }

  @Test
  void the_question_is_normalised_but_never_guessed() {
    assertThat(
            lookups
                .lookupStatute(" qc ", " CCQ-1991 ", " 1942 ", " FR ", LocalDate.of(2025, 6, 1))
                .outcome())
        .isEqualTo(Outcome.FOUND);
    assertThatThrownBy(() -> lookups.lookupStatute("QC", "CCQ-1991", " ", "fr", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> lookups.lookupStatute("QC", "CCQ-1991", "1942", "de", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> lookups.lookupStatute("QC", "CCQ-1991", "1942", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_switched_off_corpus_answers_nothing() {
    settings.enabled = false;

    assertThat(at("1942", "fr", LocalDate.of(2025, 6, 1)).outcome())
        .isEqualTo(Outcome.LAW_CORPUS_DISABLED);
  }

  @Test
  void a_text_that_no_longer_gives_its_recorded_hash_is_an_integrity_error_with_no_text() {
    store.rewriteText("CCQ-1991", "1942", "fr", "Le bail est d'un an.");

    StatuteLookup lookup = at("1942", "fr", LocalDate.of(2025, 6, 1));

    assertThat(lookup.outcome()).isEqualTo(Outcome.LAW_INTEGRITY_ERROR);
    assertThat(lookup.version()).isNull();
    // The other articles are untouched and still read.
    assertThat(at("1943", "fr", LocalDate.of(2025, 6, 1)).outcome()).isEqualTo(Outcome.FOUND);
  }
}
