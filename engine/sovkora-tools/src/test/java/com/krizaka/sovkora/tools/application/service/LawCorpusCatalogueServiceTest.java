package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.tools.application.port.law.LawCorpusCatalogue;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleHistory;
import com.krizaka.sovkora.tools.domain.model.law.LawImportSummary;
import com.krizaka.sovkora.tools.domain.model.law.LawInstrumentSummary;
import com.krizaka.sovkora.tools.domain.model.law.LawVersionSummary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The Corpus tab: an article by point in time, metadata only. */
class LawCorpusCatalogueServiceTest {

  private static final LawVersionSummary OLD =
      new LawVersionSummary(
          LocalDate.parse("1994-01-01"),
          LocalDate.parse("1999-04-01"),
          "a".repeat(64),
          "https://www.legisquebec.gouv.qc.ca/fr/version/lc/CCQ-1991?code=se:1931",
          UUID.randomUUID(),
          120);
  private static final LawVersionSummary NOW =
      new LawVersionSummary(
          LocalDate.parse("1999-04-01"),
          null,
          "b".repeat(64),
          OLD.officialUrl(),
          UUID.randomUUID(),
          140);

  private final List<Integer> limits = new ArrayList<>();
  private final LawCorpusCatalogueService service =
      new LawCorpusCatalogueService(
          new LawCorpusCatalogue() {
            @Override
            public List<LawInstrumentSummary> instruments() {
              return List.of();
            }

            @Override
            public List<LawImportSummary> imports(int limit) {
              limits.add(limit);
              return List.of();
            }

            @Override
            public List<LawVersionSummary> versions(
                String jurisdiction, String instrument, String ref, String language) {
              return "1931".equals(ref) ? List.of(NOW, OLD) : List.of();
            }
          });

  @Test
  void the_version_in_force_on_the_day_is_named_beside_the_history() {
    LawArticleHistory before =
        service.article("qc", "CCQ-1991", "1931", "fr", LocalDate.parse("1998-12-31"));
    LawArticleHistory after =
        service.article("QC", "CCQ-1991", "1931", "fr", LocalDate.parse("1999-04-01"));

    assertThat(before.jurisdiction()).isEqualTo("QC");
    assertThat(before.inForce()).isEqualTo(OLD);
    assertThat(after.inForce()).isEqualTo(NOW);
    assertThat(after.history()).containsExactly(NOW, OLD);
    assertThat(
            service
                .article("QC", "CCQ-1991", "9999", "fr", LocalDate.parse("2026-10-20"))
                .inForce())
        .isNull();
  }

  @Test
  void a_question_is_whole_and_a_page_is_bounded() {
    LocalDate day = LocalDate.parse("2026-10-20");

    assertThatThrownBy(() -> service.article("QC", "CCQ-1991", "1931", "de", day))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.article("QC", " ", "1931", "fr", day))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.article("QC", "CCQ-1991", "1931", "fr", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.imports(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.imports(201)).isInstanceOf(IllegalArgumentException.class);
    service.imports(50);
    assertThat(limits).containsExactly(50);
  }
}
