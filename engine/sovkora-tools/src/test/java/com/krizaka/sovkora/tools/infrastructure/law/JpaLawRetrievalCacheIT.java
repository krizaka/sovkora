package com.krizaka.sovkora.tools.infrastructure.law;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.research.Citation;
import com.krizaka.sovkora.core.domain.model.research.Claim;
import com.krizaka.sovkora.core.domain.model.research.ClaimStatus;
import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import com.krizaka.sovkora.tools.domain.model.law.CitedArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawImportMode;
import com.krizaka.sovkora.tools.domain.model.law.LawImportStart;
import com.krizaka.sovkora.tools.domain.model.law.StoredRetrieval;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
 * {@code law_retrieval} on PostgreSQL: the JSONB round trip, the stale mark that a changed or
 * closed cited article sets in the same transaction, and one row for two calls computing the same
 * key at once. Every test commits for real.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaLawCorpusStore.class, JpaLawRetrievalCache.class})
class JpaLawRetrievalCacheIT extends AbstractContainerIntegrationTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity")
  @EnableJpaRepositories("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository")
  static class Wiring {}

  private static final Instant T0 = Instant.parse("2026-10-24T06:00:00Z");
  private static final LocalDate BUCKET = LocalDate.of(2020, 1, 1);

  @Autowired private JpaLawCorpusStore corpus;
  @Autowired private JpaLawRetrievalCache cache;
  @Autowired private JdbcTemplate jdbc;

  private String instrument;
  private String kind;
  private UUID importId;

  @BeforeEach
  void fresh() {
    LawTestSchema.apply(jdbc);
    // One instrument and kind per test: the tests share the database and commit.
    instrument = "T-" + UUID.randomUUID().toString().substring(0, 8);
    kind = "kind-" + UUID.randomUUID().toString().substring(0, 8);
    importId =
        corpus.beginImport(
            new LawImportStart(
                "donnees-quebec", LawImportMode.AUTOMATED, "https://d.test/x.zip", null, null, T0));
  }

  private LawArticle article(String ref, String text, LocalDate from) {
    return new LawArticle(
        "QC",
        instrument,
        ref,
        "fr",
        from,
        text,
        "https://o.test/" + instrument + "/" + ref,
        "https://d.test/x.zip");
  }

  private StoredRetrieval retrieval(String kindId, String... refs) {
    List<CitedArticle> cited =
        java.util.Arrays.stream(refs)
            .map(r -> new CitedArticle(instrument, r, "fr", "a".repeat(64)))
            .toList();
    Claim claim =
        new Claim(
            "art. " + refs[0] + " : un préavis de 24 heures",
            ClaimStatus.VERIFIED,
            null,
            List.of(
                new Citation(
                    "https://o.test/" + instrument + "/" + refs[0],
                    "un préavis de 24 heures",
                    "a".repeat(64),
                    "2026-10-24T06:00:00Z")));
    return new StoredRetrieval(
        "QC", kindId, "fr", BUCKET, cited, List.of(claim), UUID.randomUUID(), T0, false);
  }

  @Test
  @DisplayName("a retrieval round-trips through JSONB: articles, claims, citations, status")
  void roundTrip() {
    StoredRetrieval stored = retrieval(kind, "1931", "1932");
    cache.save(stored);

    assertThat(cache.find("QC", kind, "fr", BUCKET)).contains(stored);
    assertThat(cache.find("QC", kind, "fr", BUCKET.plusDays(1))).isEmpty();
  }

  @Test
  @DisplayName("a French retrieval is never served for English: the language is part of the key")
  void theLanguageIsPartOfTheKey() {
    StoredRetrieval fr = retrieval(kind, "1931");
    cache.save(fr);

    assertThat(cache.find("QC", kind, "en", BUCKET)).isEmpty();

    StoredRetrieval en =
        new StoredRetrieval(
            "QC", kind, "en", BUCKET, fr.articles(), List.of(), UUID.randomUUID(), T0, false);
    cache.save(en);
    assertThat(cache.find("QC", kind, "fr", BUCKET)).contains(fr);
    assertThat(cache.find("QC", kind, "en", BUCKET)).contains(en);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from law_retrieval where kind_id = ?", Integer.class, kind))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("a new version of a cited article marks the retrievals citing it stale, only those")
  void replaceMarksStale() {
    LawArticle v1 = article("1932", "Trois jours.", LocalDate.of(2020, 1, 1));
    corpus.add(importId, "donnees-quebec", v1, T0);
    String other = kind + "-other";
    cache.save(retrieval(kind, "1931", "1932"));
    cache.save(retrieval(other, "1931"));

    corpus.replace(importId, v1, article("1932", "Quatre jours.", LocalDate.of(2026, 10, 22)), T0);

    assertThat(cache.find("QC", kind, "fr", BUCKET))
        .hasValueSatisfying(r -> assertThat(r.stale()).isTrue());
    assertThat(cache.find("QC", other, "fr", BUCKET))
        .hasValueSatisfying(r -> assertThat(r.stale()).isFalse());
  }

  @Test
  @DisplayName("an article that leaves force marks the retrievals citing it stale")
  void closeMarksStale() {
    LawArticle v1 = article("1933", "Un.", LocalDate.of(2020, 1, 1));
    corpus.add(importId, "donnees-quebec", v1, T0);
    cache.save(retrieval(kind, "1933"));

    corpus.close(v1, LocalDate.of(2026, 10, 22));

    assertThat(cache.find("QC", kind, "fr", BUCKET))
        .hasValueSatisfying(r -> assertThat(r.stale()).isTrue());
  }

  @Test
  @DisplayName("a refused replacement marks nothing: the stale mark is in the same transaction")
  void aRefusedReplacementMarksNothing() {
    LawArticle v1 = article("1934", "Un.", LocalDate.of(2020, 1, 1));
    corpus.add(importId, "donnees-quebec", v1, T0);
    cache.save(retrieval(kind, "1934"));

    // The new text does not take effect after the one it replaces: refused.
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                corpus.replace(
                    importId, v1, article("1934", "Deux.", LocalDate.of(2019, 1, 1)), T0))
        .isInstanceOf(IllegalStateException.class);

    assertThat(cache.find("QC", kind, "fr", BUCKET))
        .hasValueSatisfying(r -> assertThat(r.stale()).isFalse());
  }

  @Test
  @DisplayName("saving the recomputed retrieval replaces the stale row with a fresh one")
  void recomputeReplacesTheStaleRow() {
    LawArticle v1 = article("1935", "Un.", LocalDate.of(2020, 1, 1));
    corpus.add(importId, "donnees-quebec", v1, T0);
    cache.save(retrieval(kind, "1935"));
    corpus.close(v1, LocalDate.of(2026, 10, 22));

    cache.save(retrieval(kind, "1935"));

    assertThat(cache.find("QC", kind, "fr", BUCKET))
        .hasValueSatisfying(r -> assertThat(r.stale()).isFalse());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from law_retrieval where kind_id = ?", Integer.class, kind))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("two calls computing the same key at once leave one row and fail neither")
  void concurrentSavesLeaveOneRow() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<?>> saves = new java.util.ArrayList<>();
      for (int i = 0; i < 8; i++) {
        saves.add(
            pool.submit(
                () -> {
                  go.await();
                  cache.save(retrieval(kind, "1931", "1932"));
                  return null;
                }));
      }
      go.countDown();
      for (Future<?> save : saves) {
        save.get();
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(
            jdbc.queryForObject(
                "select count(*) from law_retrieval where kind_id = ?", Integer.class, kind))
        .isEqualTo(1);
  }
}
