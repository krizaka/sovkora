package com.krizaka.sovkora.tools.infrastructure.law;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import com.krizaka.sovkora.tools.domain.model.law.KindDemand;
import com.krizaka.sovkora.tools.domain.model.law.LawImportSummary;
import com.krizaka.sovkora.tools.domain.model.law.LawInstrumentSummary;
import com.krizaka.sovkora.tools.domain.model.law.LawVersionSummary;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Corpus and Demand tabs on PostgreSQL: instruments with their counts, imports newest first, an
 * article's versions without its text, and {@code kind_demand} the most asked first.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaLawCorpusCatalogue.class, JpaKindDemandStore.class})
class LawReviewReadsIT extends AbstractContainerIntegrationTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity")
  @EnableJpaRepositories("com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository")
  static class Wiring {}

  private static final Instant T0 = Instant.parse("2026-10-24T06:00:00Z");

  @Autowired private JpaLawCorpusCatalogue catalogue;
  @Autowired private JpaKindDemandStore demand;
  @Autowired private JdbcTemplate jdbc;

  private String code;

  @BeforeEach
  void fresh() {
    LawTestSchema.apply(jdbc);
    code = "T-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  @DisplayName("an instrument counts its open articles and its versions; an article shows no text")
  void instrumentsAndVersions() {
    UUID instrument = UUID.randomUUID();
    UUID older = UUID.randomUUID();
    UUID newer = UUID.randomUUID();
    importRow(older, T0);
    importRow(newer, T0.plusSeconds(3600));
    jdbc.update(
        "insert into law_instrument (id, jurisdiction, code, title_fr, official_url_template,"
            + " source_key) values (?, 'QC', ?, 'Loi de test', ?, 'donnees-quebec')",
        instrument,
        code,
        "https://www.legisquebec.gouv.qc.ca/fr/version/lc/" + code + "?code=se:{ref}");
    version(
        instrument, "1", LocalDate.parse("2000-01-01"), LocalDate.parse("2020-01-01"), "a", older);
    version(instrument, "1", LocalDate.parse("2020-01-01"), null, "b", newer);
    version(instrument, "2", LocalDate.parse("2000-01-01"), null, "c", older);

    LawInstrumentSummary summary =
        catalogue.instruments().stream()
            .filter(i -> i.code().equals(code))
            .findFirst()
            .orElseThrow();
    assertThat(summary.articles()).isEqualTo(2);
    assertThat(summary.versions()).isEqualTo(3);
    assertThat(summary.titleFr()).isEqualTo("Loi de test");

    List<LawVersionSummary> history = catalogue.versions("QC", code, "1", "fr");
    assertThat(history)
        .extracting(LawVersionSummary::inForceFrom)
        .containsExactly(LocalDate.parse("2020-01-01"), LocalDate.parse("2000-01-01"));
    assertThat(history.getFirst().officialUrl()).endsWith("?code=se:1");
    assertThat(history.getFirst().length()).isEqualTo("texte b".length());
    assertThat(catalogue.versions("QC", "NOPE", "1", "fr")).isEmpty();

    List<LawImportSummary> imports = catalogue.imports(2);
    assertThat(imports).hasSize(2);
    assertThat(imports.getFirst().startedAt()).isAfterOrEqualTo(imports.get(1).startedAt());
  }

  @Test
  @DisplayName("the demand lists the most asked kind first and never a document")
  void demandMostAskedFirst() {
    String rare = "qc-" + UUID.randomUUID().toString().substring(0, 6);
    String asked = "qc-" + UUID.randomUUID().toString().substring(0, 6);
    jdbc.update(
        "insert into kind_demand (jurisdiction, kind_id, count, first_seen, last_seen) values"
            + " ('QC', ?, 1, ?, ?), ('QC', ?, 9999999, ?, ?)",
        rare,
        Timestamp.from(T0),
        Timestamp.from(T0),
        asked,
        Timestamp.from(T0),
        Timestamp.from(T0));

    List<KindDemand> rows = demand.list();

    assertThat(rows.getFirst().kindId()).isEqualTo(asked);
    assertThat(rows.getFirst().count()).isEqualTo(9_999_999);
    assertThat(rows).extracting(KindDemand::kindId).contains(rare);
  }

  private void importRow(UUID id, Instant at) {
    jdbc.update(
        "insert into law_import (id, source_key, mode, started_at, finished_at, articles_new,"
            + " articles_changed, articles_unchanged, status) values (?, 'donnees-quebec',"
            + " 'AUTOMATED', ?, ?, 1, 0, 0, 'SUCCEEDED')",
        id,
        Timestamp.from(at),
        Timestamp.from(at));
  }

  private void version(
      UUID instrument, String ref, LocalDate from, LocalDate to, String text, UUID importId) {
    jdbc.update(
        "insert into law_article_version (instrument_id, ref, language, in_force_from,"
            + " in_force_to, text, content_hash, import_id, first_seen_at, last_seen_at) values"
            + " (?, ?, 'fr', ?, ?, ?, ?, ?, now(), now())",
        instrument,
        ref,
        Date.valueOf(from),
        to == null ? null : Date.valueOf(to),
        "texte " + text,
        text.repeat(64),
        importId);
  }
}
