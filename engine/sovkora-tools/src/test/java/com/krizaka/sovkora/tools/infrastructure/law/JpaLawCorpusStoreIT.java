package com.krizaka.sovkora.tools.infrastructure.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import com.krizaka.sovkora.tools.domain.model.law.*;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawArticleVersionEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawImportEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawArticleVersionRepository;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawImportRepository;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.LawInstrumentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The law tables on PostgreSQL: the period key, the partial unique index of open versions, the
 * close-then-insert transaction and its lock, the as-of selection and the import rows. Every test
 * commits for real ({@code NOT_SUPPORTED}) so that the locks and the rollbacks are the database's.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(JpaLawCorpusStore.class)
class JpaLawCorpusStoreIT extends AbstractContainerIntegrationTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity")
  @EnableJpaRepositories("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository")
  static class Wiring {}

  private static final Instant T0 = Instant.parse("2026-10-23T06:00:00Z");
  private static final String SOURCE = "donnees-quebec";

  @Autowired private JpaLawCorpusStore store;
  @Autowired private LawArticleVersionRepository versions;
  @Autowired private LawInstrumentRepository instruments;
  @Autowired private LawImportRepository imports;
  @Autowired private JdbcTemplate jdbc;

  private String instrument;
  private UUID importId;

  @BeforeEach
  void freshInstrument() {
    LawTestSchema.apply(jdbc);
    // One instrument code per test: the tests share the database and commit.
    instrument = "T-" + UUID.randomUUID().toString().substring(0, 8);
    importId =
        store.beginImport(
            new LawImportStart(
                SOURCE, LawImportMode.AUTOMATED, "https://d.test/x.zip", null, null, T0));
  }

  private LawArticle article(String ref, String text, LocalDate from) {
    return new LawArticle(
        "QC",
        instrument,
        ref,
        "fr",
        from,
        text,
        "https://www.legisquebec.gouv.qc.ca/fr/version/lc/" + instrument + "?code=se:" + ref,
        "https://d.test/x.zip");
  }

  private List<LawArticleVersionEntity> rowsOf(String ref) {
    UUID instrumentId =
        instruments.findByJurisdictionAndCode("QC", instrument).orElseThrow().getId();
    return versions.findAll().stream()
        .filter(
            v -> v.getId().getInstrumentId().equals(instrumentId) && v.getId().getRef().equals(ref))
        .toList();
  }

  private List<LawArticleVersion> open() {
    return store.currentVersions("QC", instrument);
  }

  @Test
  @DisplayName("the first article creates its instrument once, with the template of its citation")
  void instrumentIsCreatedOnce() {
    store.add(importId, SOURCE, article("1", "A.", LocalDate.of(1999, 4, 1)), T0);
    store.add(importId, SOURCE, article("2", "B.", LocalDate.of(1999, 4, 1)), T0);

    assertThat(instruments.findAll().stream().filter(i -> i.getCode().equals(instrument)))
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.getSourceKey()).isEqualTo(SOURCE);
              assertThat(i.getOfficialUrlTemplate()).contains("{lang}", "{ref}", instrument);
            });
    assertThat(open()).hasSize(2);
    assertThat(open().getFirst().article().officialUrl())
        .startsWith("https://www.legisquebec.gouv.qc.ca/fr/version/lc/" + instrument);
    assertThat(open().getFirst().importId()).isEqualTo(importId);
    assertThat(open().getFirst().article().sourceUrl()).isEqualTo("https://d.test/x.zip");
  }

  @Test
  @DisplayName(
      "a version is read back with the hash recorded with its row, not one recomputed (#89)")
  void readBackCarriesTheRecordedHash() {
    LawArticle a = article("1931", "Avis de 24 heures.", LocalDate.of(1994, 1, 1));
    store.add(importId, SOURCE, a, T0);
    LocalDate day = LocalDate.of(2026, 1, 1);

    assertThat(store.inForceOn("QC", instrument, "1931", "fr", day))
        .hasValueSatisfying(
            v -> {
              assertThat(v.recordedHash()).isEqualTo(a.contentHash().hex());
              assertThat(v.intact()).isTrue();
            });

    // A hand edit of the text, the row's content_hash left as it was.
    UUID instrumentId =
        instruments.findByJurisdictionAndCode("QC", instrument).orElseThrow().getId();
    jdbc.update(
        "UPDATE law_article_version SET text = ? WHERE instrument_id = ? AND ref = ?",
        "Avis de 2 heures.",
        instrumentId,
        "1931");

    assertThat(store.inForceOn("QC", instrument, "1931", "fr", day))
        .hasValueSatisfying(
            v -> {
              assertThat(v.recordedHash()).isEqualTo(a.contentHash().hex());
              assertThat(v.intact()).isFalse();
            });
  }

  @Test
  @DisplayName("a changed text closes the old row at the new day and adds the new row")
  void replaceClosesAndAdds() {
    LawArticle v1 = article("1942", "Trois mois.", LocalDate.of(1999, 4, 1));
    LawArticle v2 = article("1942", "Quatre mois.", LocalDate.of(2026, 10, 1));
    store.add(importId, SOURCE, v1, T0);

    store.replace(importId, v1, v2, T0.plusSeconds(60));

    assertThat(open())
        .singleElement()
        .satisfies(v -> assertThat(v.article().text()).isEqualTo("Quatre mois."));
    assertThat(rowsOf("1942"))
        .filteredOn(v -> "Trois mois.".equals(v.getText()))
        .singleElement()
        .satisfies(v -> assertThat(v.getInForceTo()).isEqualTo(LocalDate.of(2026, 10, 1)));
    assertThat(store.inForceOn("QC", instrument, "1942", "fr", LocalDate.of(2026, 9, 30)))
        .map(v -> v.article().text())
        .contains("Trois mois.");
    assertThat(store.inForceOn("QC", instrument, "1942", "fr", LocalDate.of(2026, 10, 1)))
        .map(v -> v.article().text())
        .contains("Quatre mois.");
    assertThat(store.inForceOn("QC", instrument, "1942", "fr", LocalDate.of(1999, 3, 31)))
        .isEmpty();
  }

  @Test
  @DisplayName("a reverted text is a third row with the same hash and a new period")
  void reverted() {
    LawArticle a = article("1942", "Trois mois.", LocalDate.of(1999, 4, 1));
    LawArticle b = article("1942", "Quatre mois.", LocalDate.of(2026, 10, 1));
    LawArticle aAgain = article("1942", "Trois mois.", LocalDate.of(2027, 1, 1));
    store.add(importId, SOURCE, a, T0);
    store.replace(importId, a, b, T0);

    store.replace(importId, b, aAgain, T0);

    assertThat(
            versions.findAll().stream()
                .filter(
                    v ->
                        "1942".equals(v.getId().getRef())
                            && instruments
                                .findById(v.getId().getInstrumentId())
                                .orElseThrow()
                                .getCode()
                                .equals(instrument)))
        .hasSize(3);
    assertThat(store.inForceOn("QC", instrument, "1942", "fr", LocalDate.of(2027, 6, 1)))
        .hasValueSatisfying(
            v -> assertThat(v.article().inForceFrom()).isEqualTo(LocalDate.of(2027, 1, 1)));
    assertThat(store.inForceOn("QC", instrument, "1942", "fr", LocalDate.of(2020, 6, 1)))
        .hasValueSatisfying(
            v -> assertThat(v.article().inForceFrom()).isEqualTo(LocalDate.of(1999, 4, 1)));
  }

  @Test
  @DisplayName(
      "the close and the insert are one transaction: a refused insert reopens nothing, closes nothing")
  void replaceIsAtomic() {
    LawArticle v1 = article("5", "Un.", LocalDate.of(2020, 1, 1));
    store.add(importId, SOURCE, v1, T0);
    UUID instrumentId =
        instruments.findByJurisdictionAndCode("QC", instrument).orElseThrow().getId();
    // Tamper: a row already exists at the day the new text would start.
    jdbc.update(
        "insert into law_article_version (instrument_id, ref, language, in_force_from, in_force_to,"
            + " text, content_hash, import_id, first_seen_at, last_seen_at)"
            + " values (?, '5', 'fr', ?, ?, 'x', ?, ?, now(), now())",
        instrumentId,
        LocalDate.of(2027, 1, 1),
        LocalDate.of(2027, 2, 1),
        "b".repeat(64),
        importId);

    assertThatThrownBy(
            () -> store.replace(importId, v1, article("5", "Deux.", LocalDate.of(2027, 1, 1)), T0))
        .isInstanceOf(IllegalStateException.class);

    assertThat(open())
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.article().text()).isEqualTo("Un.");
              assertThat(v.inForceTo()).isNull();
            });
  }

  @Test
  @DisplayName(
      "a replace is refused when the open version is no longer the one compared, or the new day is not later")
  void replaceChecksWhatItReplaces() {
    LawArticle v1 = article("7", "Un.", LocalDate.of(2020, 1, 1));
    store.add(importId, SOURCE, v1, T0);

    assertThatThrownBy(
            () ->
                store.replace(
                    importId,
                    article("7", "Autre.", LocalDate.of(2020, 1, 1)),
                    article("7", "Deux.", LocalDate.of(2021, 1, 1)),
                    T0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no longer the one");
    assertThatThrownBy(
            () -> store.replace(importId, v1, article("7", "Deux.", LocalDate.of(2020, 1, 1)), T0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not take effect after");
    assertThat(open())
        .singleElement()
        .satisfies(v -> assertThat(v.article().text()).isEqualTo("Un."));
  }

  @Test
  @DisplayName("a closed article is unknown from the day it was closed")
  void closed() {
    LawArticle v1 = article("9", "Un.", LocalDate.of(2020, 1, 1));
    store.add(importId, SOURCE, v1, T0);

    store.close(v1, LocalDate.of(2026, 10, 23));

    assertThat(open()).isEmpty();
    assertThat(store.inForceOn("QC", instrument, "9", "fr", LocalDate.of(2026, 10, 22)))
        .isPresent();
    assertThat(store.inForceOn("QC", instrument, "9", "fr", LocalDate.of(2026, 10, 23))).isEmpty();
    assertThatThrownBy(() -> store.close(v1, LocalDate.of(2026, 10, 24)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("touching moves last_seen_at of the articles confirmed, and of no other")
  void touch() {
    LawArticle one = article("1", "Un.", LocalDate.of(2020, 1, 1));
    LawArticle two = article("2", "Deux.", LocalDate.of(2020, 1, 1));
    store.add(importId, SOURCE, one, T0);
    store.add(importId, SOURCE, two, T0);
    Instant later = T0.plusSeconds(3600);

    store.touch("QC", instrument, List.of(one), later);

    assertThat(open())
        .filteredOn(v -> "1".equals(v.article().ref()))
        .singleElement()
        .satisfies(v -> assertThat(v.lastSeenAt()).isEqualTo(later));
    assertThat(open())
        .filteredOn(v -> "2".equals(v.article().ref()))
        .singleElement()
        .satisfies(v -> assertThat(v.lastSeenAt()).isEqualTo(T0));
  }

  @Test
  @DisplayName("a second add of an open article is refused and never overwrites it")
  void addNeverOverwrites() {
    store.add(importId, SOURCE, article("3", "Un.", LocalDate.of(2020, 1, 1)), T0);

    assertThatThrownBy(
            () -> store.add(importId, SOURCE, article("3", "Autre.", LocalDate.of(2020, 1, 1)), T0))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> store.add(importId, SOURCE, article("3", "Autre.", LocalDate.of(2021, 1, 1)), T0))
        .isInstanceOf(IllegalStateException.class);

    assertThat(open())
        .singleElement()
        .satisfies(v -> assertThat(v.article().text()).isEqualTo("Un."));
  }

  @Test
  @DisplayName(
      "two imports replacing the same version at once: one wins, one is refused, one version stays open")
  void concurrentReplace() throws Exception {
    LawArticle v1 = article("11", "Un.", LocalDate.of(2020, 1, 1));
    store.add(importId, SOURCE, v1, T0);
    LawArticle fromA = article("11", "Deux A.", LocalDate.of(2026, 10, 1));
    LawArticle fromB = article("11", "Deux B.", LocalDate.of(2026, 11, 1));

    List<Object> outcomes =
        race(
            () -> {
              store.replace(importId, v1, fromA, T0);
              return "A";
            },
            () -> {
              store.replace(importId, v1, fromB, T0);
              return "B";
            });

    assertThat(outcomes).filteredOn(String.class::isInstance).hasSize(1);
    assertThat(outcomes).filteredOn(IllegalStateException.class::isInstance).hasSize(1);
    assertThat(open()).hasSize(1);
    assertThat(
            versions.findAll().stream()
                .filter(
                    v ->
                        v.getInForceTo() == null
                            && instruments
                                .findById(v.getId().getInstrumentId())
                                .orElseThrow()
                                .getCode()
                                .equals(instrument)))
        .hasSize(1);
  }

  @Test
  @DisplayName("two imports adding the same new article at once: one wins, one is refused")
  void concurrentAdd() throws Exception {
    LawArticle a = article("12", "Un A.", LocalDate.of(2020, 1, 1));
    LawArticle b = article("12", "Un B.", LocalDate.of(2020, 1, 1));
    // The instrument row is shared: create it first, as the first import of any article does.
    store.add(importId, SOURCE, article("0", "Zéro.", LocalDate.of(2020, 1, 1)), T0);

    List<Object> outcomes =
        race(
            () -> {
              store.add(importId, SOURCE, a, T0);
              return "A";
            },
            () -> {
              store.add(importId, SOURCE, b, T0);
              return "B";
            });

    assertThat(outcomes).filteredOn(String.class::isInstance).hasSize(1);
    assertThat(outcomes).filteredOn(IllegalStateException.class::isInstance).hasSize(1);
    assertThat(open().stream().filter(v -> "12".equals(v.article().ref()))).hasSize(1);
  }

  @Test
  @DisplayName("an import row keeps its mode, source, who, hash, counts and end")
  void importRows() {
    String sha = "c".repeat(64);
    UUID manual =
        store.beginImport(
            new LawImportStart(
                SOURCE, LawImportMode.MANUAL, "https://f.test/ccq.xml", sha, "oussama", T0));

    LawImportEntity running = imports.findById(manual).orElseThrow();
    assertThat(running.getStatus()).isEqualTo("RUNNING");
    assertThat(running.getMode()).isEqualTo("MANUAL");
    assertThat(running.getImportedBy()).isEqualTo("oussama");

    store.finishImport(
        manual,
        new LawImportResult(
            LawImportStatus.SUCCEEDED,
            "\"e1\"",
            sha,
            LocalDate.of(2026, 10, 21),
            6,
            1,
            2,
            T0.plusSeconds(5)));

    LawImportEntity done = imports.findById(manual).orElseThrow();
    assertThat(done.getStatus()).isEqualTo("SUCCEEDED");
    assertThat(done.getDatasetSha256()).isEqualTo(sha);
    assertThat(done.getDatasetEtag()).isEqualTo("\"e1\"");
    assertThat(done.getConsolidatedTo()).isEqualTo(LocalDate.of(2026, 10, 21));
    assertThat(done.getArticlesNew()).isEqualTo(6);
    assertThat(done.getArticlesChanged()).isEqualTo(1);
    assertThat(done.getArticlesUnchanged()).isEqualTo(2);
    assertThat(done.getFinishedAt()).isEqualTo(T0.plusSeconds(5));
  }

  @Test
  @DisplayName("the database itself refuses a manual import row with no who or no hash")
  void manualImportIsTraceableInTheDatabase() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into law_import (id, source_key, mode, started_at, articles_new,"
                        + " articles_changed, articles_unchanged, status)"
                        + " values (?, ?, 'MANUAL', now(), 0, 0, 0, 'RUNNING')",
                    UUID.randomUUID(),
                    SOURCE))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("the validator comes from the last successful automated import of the dataset")
  void lastEtag() {
    String url = "https://d.test/" + instrument + ".zip";
    UUID ok =
        store.beginImport(new LawImportStart(SOURCE, LawImportMode.AUTOMATED, url, null, null, T0));
    store.finishImport(
        ok,
        new LawImportResult(
            LawImportStatus.SUCCEEDED, "\"v1\"", "d".repeat(64), null, 0, 0, 0, T0));
    UUID failed =
        store.beginImport(
            new LawImportStart(
                SOURCE, LawImportMode.AUTOMATED, url, null, null, T0.plusSeconds(10)));
    store.finishImport(
        failed,
        new LawImportResult(
            LawImportStatus.FAILED, "\"v2\"", null, null, 0, 0, 0, T0.plusSeconds(11)));

    assertThat(store.lastEtag(SOURCE, url)).contains("\"v1\"");
    assertThat(store.lastEtag(SOURCE, "https://d.test/never.zip")).isEmpty();
  }

  private static List<Object> race(Callable<Object> first, Callable<Object> second)
      throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch go = new CountDownLatch(1);
      List<Future<Object>> futures = new java.util.ArrayList<>();
      for (Callable<Object> task : List.of(first, second)) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  try {
                    return task.call();
                  } catch (IllegalStateException e) {
                    return e;
                  }
                }));
      }
      ready.await();
      go.countDown();
      List<Object> outcomes = new java.util.ArrayList<>();
      for (Future<Object> future : futures) {
        outcomes.add(future.get());
      }
      return outcomes;
    } finally {
      pool.shutdownNow();
    }
  }
}
