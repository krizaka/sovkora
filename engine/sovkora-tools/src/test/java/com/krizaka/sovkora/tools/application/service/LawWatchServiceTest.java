package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.tools.application.port.law.LawChangeRequests;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleVersion;
import com.krizaka.sovkora.tools.domain.model.law.LawChange;
import com.krizaka.sovkora.tools.domain.model.law.LawImportMode;
import com.krizaka.sovkora.tools.domain.model.law.LawImportStatus;
import com.krizaka.sovkora.tools.domain.model.law.LawWatchReport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The importers on recorded XML: the first real Données Québec slice of C.c.Q. (1851-2000), then
 * derived variants of it (one article amended, one removed, one reverted). No network, no database:
 * the corpus is in memory, with the semantics of the store port.
 */
class LawWatchServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), ZoneOffset.UTC);
  private static final String RECORDED = "/law/CCQ-1991_FR-slice.xml";
  private static final String AMENDED = "/law/CCQ-1991_FR-slice-amended.xml";
  private static final String UNDATED = "/law/CCQ-1991_FR-slice-amended-undated.xml";
  private static final String REMOVED = "/law/CCQ-1991_FR-slice-1942-removed.xml";
  private static final String REVERTED = "/law/CCQ-1991_FR-slice-reverted.xml";

  private InMemoryLawCorpus store;
  private RecordingRequests requests;
  private FixtureLawSource source;
  private FixedLawSettings settings;

  @BeforeEach
  void setUp() {
    store = new InMemoryLawCorpus();
    requests = new RecordingRequests();
    source = new FixtureLawSource();
    settings = new FixedLawSettings();
    settings.instruments =
        List.of(
            com.krizaka.sovkora.tools.domain.model.law.LawTarget.wholeInstrument("QC", "CCQ-1991"));
  }

  private LawWatchService watcher() {
    LawImportService importer = new LawImportService(store, settings, requests, CLOCK);
    return new LawWatchService(List.of(source), settings, importer);
  }

  private List<LawArticleVersion> history1942() {
    return store.history("CCQ-1991", "1942", "fr");
  }

  @Test
  void the_first_import_stores_the_texts_opens_no_request_and_is_a_law_import_row() {
    source.serve(RECORDED);

    LawWatchReport report = watcher().run();

    assertThat(requests.opened).isEmpty();
    assertThat(report.stored()).isEqualTo(6);
    assertThat(store.currentVersions("QC", "CCQ-1991")).hasSize(6);
    assertThat(report.imports()).hasSize(1);
    InMemoryLawCorpus.ImportRow row = store.imports.get(report.imports().getFirst());
    assertThat(row.start.mode()).isEqualTo(LawImportMode.AUTOMATED);
    assertThat(row.start.sourceKey()).isEqualTo("donnees-quebec");
    assertThat(row.start.datasetUrl()).isEqualTo(FixtureLawSource.DATASET);
    assertThat(row.status()).isEqualTo(LawImportStatus.SUCCEEDED);
    assertThat(row.result.datasetSha256()).hasSize(64);
    assertThat(row.result.articlesNew()).isEqualTo(6);
    assertThat(row.result.articlesChanged()).isZero();
    assertThat(row.result.articlesUnchanged()).isZero();
    assertThat(store.versions).allMatch(v -> report.imports().contains(v.importId()));
  }

  @Test
  void an_unchanged_article_opens_nothing_and_only_moves_last_seen_at() {
    source.serve(RECORDED);
    watcher().run();
    Instant firstSeen = store.currentVersions("QC", "CCQ-1991").getFirst().firstSeenAt();

    LawWatchReport second = watcher().run();

    assertThat(requests.opened).isEmpty();
    assertThat(second.unchanged()).isEqualTo(6);
    assertThat(second.failures()).isEmpty();
    assertThat(store.versions).hasSize(6);
    assertThat(store.imports).hasSize(2);
    InMemoryLawCorpus.ImportRow row = store.imports.get(second.imports().getFirst());
    assertThat(row.result.articlesUnchanged()).isEqualTo(6);
    assertThat(row.result.articlesNew()).isZero();
    assertThat(store.currentVersions("QC", "CCQ-1991"))
        .allMatch(v -> v.firstSeenAt().equals(firstSeen));
  }

  @Test
  void a_changed_article_opens_one_request_closes_the_old_row_and_stores_a_new_dated_one() {
    source.serve(RECORDED);
    watcher().run();
    source.serve(AMENDED);

    LawWatchReport report = watcher().run();

    assertThat(requests.opened).hasSize(1);
    LawChange change = requests.opened.getFirst();
    assertThat(change.kind()).isEqualTo(LawChange.Kind.CHANGED);
    assertThat(change.subject().ref()).isEqualTo("1942");
    assertThat(change.previous().text()).contains("trois mois");
    assertThat(change.current().text()).contains("quatre mois");
    assertThat(report.requests()).hasSize(1);

    List<LawArticleVersion> rows = history1942();
    assertThat(rows).hasSize(2);
    assertThat(rows.getFirst().inForceTo()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(rows.getFirst().article().text()).contains("trois mois");
    assertThat(rows.get(1).inForceTo()).isNull();
    assertThat(rows.get(1).article().inForceFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(rows.get(1).article().text()).contains("quatre mois");
    assertThat(rows.get(1).importId()).isEqualTo(report.imports().getFirst());
    assertThat(store.imports.get(report.imports().getFirst()).result.articlesChanged())
        .isEqualTo(1);

    assertThat(watcher().run().requests()).isEmpty();
  }

  @Test
  void a_removed_article_is_flagged_and_closed_as_of_the_run() {
    source.serve(RECORDED);
    watcher().run();
    source.serve(REMOVED);

    watcher().run();

    assertThat(requests.opened).hasSize(1);
    LawChange change = requests.opened.getFirst();
    assertThat(change.kind()).isEqualTo(LawChange.Kind.REMOVED);
    assertThat(change.current()).isNull();
    assertThat(change.subject().ref()).isEqualTo("1942");
    assertThat(change.detectedOn()).isEqualTo(LocalDate.of(2026, 10, 23));
    assertThat(store.currentVersions("QC", "CCQ-1991"))
        .noneMatch(v -> "1942".equals(v.article().ref()));
    assertThat(history1942())
        .singleElement()
        .extracting(LawArticleVersion::inForceTo)
        .isEqualTo(LocalDate.of(2026, 10, 23));
  }

  @Test
  void a_reverted_text_is_a_new_dated_row_not_the_old_one_reopened() {
    source.serve(RECORDED);
    watcher().run();
    source.serve(AMENDED);
    watcher().run();
    source.serve(REVERTED);

    LawWatchReport report = watcher().run();

    assertThat(report.failures()).isEmpty();
    List<LawArticleVersion> rows = history1942();
    assertThat(rows).hasSize(3);
    assertThat(rows.getFirst().article().contentHash())
        .isEqualTo(rows.get(2).article().contentHash());
    assertThat(rows.getFirst().article().inForceFrom()).isEqualTo(LocalDate.of(1999, 4, 1));
    assertThat(rows.getFirst().inForceTo()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(rows.get(1).inForceTo()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(rows.get(2).article().inForceFrom()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(rows.get(2).inForceTo()).isNull();
    assertThat(rows.get(2).importId()).isEqualTo(report.imports().getFirst());
  }

  @Test
  void a_text_that_changes_without_a_new_in_force_date_is_refused_and_no_request_is_opened() {
    source.serve(RECORDED);
    watcher().run();
    source.serve(UNDATED);

    LawWatchReport report = watcher().run();

    assertThat(requests.opened).isEmpty();
    assertThat(report.failures()).singleElement().asString().contains("1942", "in-force date");
    assertThat(history1942())
        .singleElement()
        .satisfies(v -> assertThat(v.article().text()).contains("trois mois"));
    assertThat(store.imports.get(report.imports().getFirst()).status())
        .isEqualTo(LawImportStatus.FAILED);
  }

  @Test
  void a_failed_request_leaves_the_corpus_unchanged_so_the_next_run_detects_it_again() {
    source.serve(RECORDED);
    watcher().run();
    source.serve(AMENDED);
    requests.failNext = true;

    LawWatchReport report = watcher().run();

    assertThat(report.requests()).isEmpty();
    assertThat(report.failures()).hasSize(1);
    assertThat(history1942())
        .singleElement()
        .satisfies(v -> assertThat(v.article().text()).contains("trois mois"));
    assertThat(store.imports.get(report.imports().getFirst()).status())
        .isEqualTo(LawImportStatus.FAILED);

    assertThat(watcher().run().requests()).hasSize(1);
  }

  @Test
  void a_source_that_cannot_be_read_leaves_a_failed_import_row_and_changes_nothing() {
    source.serve(RECORDED);
    watcher().run();
    source.failNext();

    LawWatchReport report = watcher().run();

    assertThat(report.failures()).hasSize(1);
    InMemoryLawCorpus.ImportRow row = store.imports.get(report.imports().getFirst());
    assertThat(row.status()).isEqualTo(LawImportStatus.FAILED);
    assertThat(row.result.datasetSha256()).isNull();
    assertThat(store.versions).hasSize(6);
  }

  @Test
  void an_article_longer_than_the_setting_fails_the_whole_import_loudly_and_stores_nothing() {
    source.serve(RECORDED);
    settings.maxArticleChars = 100;

    LawWatchReport report = watcher().run();

    assertThat(report.failures())
        .singleElement()
        .asString()
        .contains("law.import.max-article-chars");
    assertThat(store.versions).isEmpty();
    assertThat(store.imports.get(report.imports().getFirst()).status())
        .isEqualTo(LawImportStatus.FAILED);
  }

  @Test
  void a_dataset_the_publisher_says_is_unchanged_is_not_read_again() {
    source.serve(RECORDED);
    source.validator("\"abc\"");
    watcher().run();
    store.etag = "\"abc\"";

    LawWatchReport second = watcher().run();

    assertThat(source.lastKnownEtag).contains("\"abc\"");
    assertThat(second.unchanged()).isZero();
    assertThat(second.failures()).isEmpty();
    InMemoryLawCorpus.ImportRow row = store.imports.get(second.imports().getFirst());
    assertThat(row.status()).isEqualTo(LawImportStatus.SUCCEEDED);
    assertThat(row.result.datasetEtag()).isEqualTo("\"abc\"");
    assertThat(store.versions).hasSize(6);
  }

  @Test
  void the_validator_is_not_sent_until_every_instrument_was_imported_whole_once() {
    source.serve(RECORDED);
    store.etag = "\"abc\"";

    watcher().run();

    assertThat(source.lastKnownEtag).isEmpty();
  }

  @Test
  void a_source_that_returns_nothing_for_a_stored_language_changes_nothing() {
    source.serve(RECORDED);
    watcher().run();
    source.serve("/law/CCQ-1991_FR-empty.xml");

    LawWatchReport report = watcher().run();

    assertThat(requests.opened).isEmpty();
    assertThat(report.failures()).singleElement().asString().contains("no fr article of CCQ-1991");
    assertThat(store.currentVersions("QC", "CCQ-1991")).hasSize(6);
  }

  @Test
  void nothing_runs_while_the_corpus_is_switched_off() {
    source.serve(RECORDED);
    settings.enabled = false;

    LawWatchReport report = watcher().run();

    assertThat(report).isEqualTo(LawWatchReport.EMPTY);
    assertThat(store.imports).isEmpty();
    assertThat(store.versions).isEmpty();
  }

  @Test
  void an_instrument_that_is_not_in_the_setting_is_not_read() {
    source.serve(RECORDED);
    settings.instruments = List.of();

    LawWatchReport report = watcher().run();

    assertThat(report.imports()).isEmpty();
    assertThat(store.versions).isEmpty();
  }

  @Test
  void a_jurisdiction_with_no_source_is_a_configuration_error() {
    settings.instruments =
        List.of(com.krizaka.sovkora.tools.domain.model.law.LawTarget.wholeInstrument("FR", "X"));

    assertThatThrownBy(() -> watcher().run())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no law source serves FR");
  }

  /** Records the requests; can be told to fail the next one. */
  static final class RecordingRequests implements LawChangeRequests {
    final List<LawChange> opened = new ArrayList<>();
    boolean failNext;

    @Override
    public String open(LawChange change) {
      if (failNext) {
        failNext = false;
        throw new IllegalStateException("GitHub answered 503");
      }
      opened.add(change);
      return "https://github.com/krizaka/sovkora-packs/pull/" + opened.size();
    }
  }
}
