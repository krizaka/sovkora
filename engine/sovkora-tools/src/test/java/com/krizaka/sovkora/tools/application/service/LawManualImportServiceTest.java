package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.LawStatuteXmlParser;
import com.krizaka.sovkora.tools.infrastructure.adapter.law.LawXmlFileReader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The manual import: the same parser and comparison, {@code mode = MANUAL}, always traceable. */
class LawManualImportServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), ZoneOffset.UTC);
  private static final String URL = "https://www.legisquebec.gouv.qc.ca/fr/pdf/cs/CCQ-1991.xml";

  private InMemoryLawCorpus store;
  private FixedLawSettings settings;
  private LawWatchServiceTest.RecordingRequests requests;
  private LawManualImportService manual;

  @BeforeEach
  void setUp() {
    store = new InMemoryLawCorpus();
    settings = new FixedLawSettings();
    requests = new LawWatchServiceTest.RecordingRequests();
    LawImportService importer = new LawImportService(store, settings, requests, CLOCK);
    manual =
        new LawManualImportService(
            List.of(new FixtureLawSource()),
            settings,
            new LawXmlFileReader(new LawStatuteXmlParser()),
            importer);
  }

  private static ManualLawFile file(String resource, String importedBy, String declared) {
    return new ManualLawFile(
        "QC",
        "CCQ-1991",
        "fr",
        FixtureLawSource.bytes(resource),
        URL,
        importedBy,
        declared,
        LocalDate.of(2026, 10, 21));
  }

  @Test
  void a_manual_import_records_who_which_file_and_its_hash() {
    byte[] content = FixtureLawSource.bytes("/law/CCQ-1991_FR-slice.xml");

    LawWatchReport report =
        manual.importFile(file("/law/CCQ-1991_FR-slice.xml", "oussama", Sha256.hex(content)));

    InMemoryLawCorpus.ImportRow row = store.imports.get(report.imports().getFirst());
    assertThat(row.start.mode()).isEqualTo(LawImportMode.MANUAL);
    assertThat(row.start.importedBy()).isEqualTo("oussama");
    assertThat(row.start.datasetSha256()).isEqualTo(Sha256.hex(content));
    assertThat(row.start.datasetUrl()).isEqualTo(URL);
    assertThat(row.start.sourceKey()).isEqualTo("donnees-quebec");
    assertThat(row.status()).isEqualTo(LawImportStatus.SUCCEEDED);
    assertThat(row.result.datasetSha256()).isEqualTo(Sha256.hex(content));
    assertThat(row.result.consolidatedTo()).isEqualTo(LocalDate.of(2026, 10, 21));
    assertThat(row.result.articlesNew()).isEqualTo(6);
  }

  @Test
  void the_same_file_gives_the_same_rows_as_the_automated_import() {
    settings.instruments = List.of(LawTarget.wholeInstrument("QC", "CCQ-1991"));
    InMemoryLawCorpus automated = new InMemoryLawCorpus();
    FixtureLawSource source = new FixtureLawSource();
    source.serve("/law/CCQ-1991_FR-slice.xml");
    new LawWatchService(
            List.of(source), settings, new LawImportService(automated, settings, null, CLOCK))
        .run();

    manual.importFile(file("/law/CCQ-1991_FR-slice.xml", "oussama", null));

    assertThat(rows(store)).containsExactlyInAnyOrderElementsOf(rows(automated));
    assertThat(rows(store)).hasSize(6);
  }

  @Test
  void a_manual_import_of_a_changed_file_closes_and_adds_like_the_watcher_and_opens_a_request() {
    manual.importFile(file("/law/CCQ-1991_FR-slice.xml", "oussama", null));

    LawWatchReport report =
        manual.importFile(file("/law/CCQ-1991_FR-slice-amended.xml", "oussama", null));

    assertThat(report.failures()).isEmpty();
    assertThat(requests.opened).hasSize(1);
    assertThat(store.history("CCQ-1991", "1942", "fr")).hasSize(2);
    assertThat(store.imports.get(report.imports().getFirst()).result.articlesChanged())
        .isEqualTo(1);
  }

  @Test
  void a_file_in_one_language_never_removes_the_other_language() {
    manual.importFile(file("/law/CCQ-1991_FR-slice.xml", "oussama", null));
    // An English article stored earlier must survive a French upload.
    store.add(
        store.beginImport(
            new com.krizaka.sovkora.tools.domain.model.law.LawImportStart(
                "donnees-quebec", LawImportMode.AUTOMATED, URL, null, null, Instant.EPOCH)),
        "donnees-quebec",
        new com.krizaka.sovkora.tools.domain.model.law.LawArticle(
            "QC",
            "CCQ-1991",
            "1851",
            "en",
            LocalDate.of(1999, 4, 1),
            "Lease is a contract.",
            URL,
            URL),
        Instant.EPOCH);

    LawWatchReport report = manual.importFile(file("/law/CCQ-1991_FR-slice.xml", "oussama", null));

    assertThat(report.failures()).isEmpty();
    assertThat(requests.opened).isEmpty();
    assertThat(store.history("CCQ-1991", "1851", "en"))
        .singleElement()
        .satisfies(v -> assertThat(v.inForceTo()).isNull());
  }

  @Test
  void who_and_the_origin_of_the_file_are_required() {
    byte[] content = FixtureLawSource.bytes("/law/CCQ-1991_FR-slice.xml");

    assertThatThrownBy(
            () -> new ManualLawFile("QC", "CCQ-1991", "fr", content, URL, " ", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("who");
    assertThatThrownBy(
            () -> new ManualLawFile("QC", "CCQ-1991", "fr", content, "", "oussama", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ManualLawFile("QC", "CCQ-1991", "fr", new byte[0], URL, "oussama", null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(store.imports).isEmpty();
  }

  @Test
  void a_declared_hash_that_is_not_the_files_is_refused_before_anything_is_written() {
    assertThatThrownBy(
            () -> manual.importFile(file("/law/CCQ-1991_FR-slice.xml", "oussama", "0".repeat(64))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SHA-256");
    assertThat(store.imports).isEmpty();
  }

  @Test
  void only_a_targeted_instrument_is_stored() {
    settings.instruments = List.of(LawTarget.wholeInstrument("CA", "E-15"));

    assertThatThrownBy(() -> manual.importFile(file("/law/CCQ-1991_FR-slice.xml", "oussama", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("law.import.instruments");
    assertThat(store.imports).isEmpty();
  }

  @Test
  void nothing_is_imported_while_the_corpus_is_off() {
    settings.enabled = false;

    assertThatThrownBy(() -> manual.importFile(file("/law/CCQ-1991_FR-slice.xml", "oussama", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("law.corpus.enabled");
    assertThat(store.imports).isEmpty();
  }

  @Test
  void a_file_that_is_not_a_statute_leaves_a_failed_import_row() {
    ManualLawFile garbage =
        new ManualLawFile("QC", "CCQ-1991", "fr", "not xml".getBytes(), URL, "oussama", null, null);

    LawWatchReport report = manual.importFile(garbage);

    assertThat(report.failures()).hasSize(1);
    InMemoryLawCorpus.ImportRow row = store.imports.get(report.imports().getFirst());
    assertThat(row.status()).isEqualTo(LawImportStatus.FAILED);
    assertThat(row.start.importedBy()).isEqualTo("oussama");
    assertThat(row.start.datasetSha256()).isEqualTo(garbage.sha256());
    assertThat(store.versions).isEmpty();
  }

  private static List<String> rows(InMemoryLawCorpus corpus) {
    return corpus.versions.stream().map(LawManualImportServiceTest::describe).toList();
  }

  private static String describe(LawArticleVersion v) {
    return v.article().ref()
        + "|"
        + v.article().language()
        + "|"
        + v.article().inForceFrom()
        + "|"
        + v.article().contentHash().hex()
        + "|"
        + v.inForceTo();
  }
}
