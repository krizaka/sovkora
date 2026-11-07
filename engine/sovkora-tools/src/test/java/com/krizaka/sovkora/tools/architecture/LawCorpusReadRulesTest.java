package com.krizaka.sovkora.tools.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.krizaka.sovkora.tools.application.port.law.LawCorpusStore;
import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleVersion;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Law text is served only after its hash is checked (issue #89, ADR-089 §2).
 *
 * <p>The bug these rules close: the store rebuilt each version from its text and the hash was then
 * recomputed from that same text, so a row whose text had been changed behind the corpus's back
 * still « matched ». Every reader that hands law text on (to a caller, to a model) must compare the
 * recomputed hash with the one recorded with the row, and the store must carry that recorded hash.
 */
class LawCorpusReadRulesTest {

  private static JavaClasses tools;

  @BeforeAll
  static void importClasses() {
    tools =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora.tools");
  }

  @Test
  @DisplayName("[LAW-001] only the readers that check integrity read an article by day")
  void onlyIntegrityCheckingReadersReadTheCorpusByDay() {
    noClasses()
        .that()
        .doNotHaveSimpleName("LawStatuteLookupService")
        .and()
        .doNotHaveSimpleName("LawCorpusResearchService")
        .should()
        .callMethod(
            LawCorpusStore.class,
            "inForceOn",
            String.class,
            String.class,
            String.class,
            String.class,
            LocalDate.class)
        .because(
            "law text read by day is handed on as a citation: it goes through"
                + " LawStatuteLookupService or LawCorpusResearchService, which refuse a version"
                + " whose text no longer gives its recorded hash (fail closed, #89)")
        .check(tools);
  }

  @Test
  @DisplayName("[LAW-002] a store carries the hash recorded with the row, never a recomputed one")
  void aStoreNeverRecomputesTheRecordedHash() {
    noClasses()
        .that()
        .resideInAPackage("com.krizaka.sovkora.tools.infrastructure..")
        .should()
        .callConstructor(
            LawArticleVersion.class,
            LawArticle.class,
            Instant.class,
            Instant.class,
            LocalDate.class,
            UUID.class)
        .because(
            "that constructor records the hash of the text it is given: a store that used it"
                + " would make every stored version look intact (#89); a store passes the"
                + " content_hash of the row")
        .check(tools);
  }
}
