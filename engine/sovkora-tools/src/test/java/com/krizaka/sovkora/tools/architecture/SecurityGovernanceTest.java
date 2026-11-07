package com.krizaka.sovkora.tools.architecture;

import com.krizaka.test.architecture.CodeRules;
import com.krizaka.test.architecture.FrozenRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The security lessons of ADR-013 (krizaka-build#102, #103), as the shared {@link CodeRules},
 * frozen: the debt that exists is recorded in {@code archunit_store/}, a new violation fails.
 */
class SecurityGovernanceTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> location.contains("sovkora-tools"))
            .importPackages("com.krizaka.sovkora.tools");
  }

  @Test
  @DisplayName("No mutable static state")
  void noMutableStaticFields() {
    FrozenRules.check(CodeRules.noStaticMutableStateRule(), classes);
  }

  @Test
  @DisplayName("System.getenv is read in configuration only")
  void noGetenvOutsideConfig() {
    FrozenRules.check(CodeRules.noGetenvOutsideConfigRule("..config.."), classes);
  }

  @Test
  @DisplayName("No user content in the query of a GET")
  void noContentOnGet() {
    FrozenRules.check(CodeRules.noContentOnGetRule(), classes);
  }

  @Test
  @DisplayName("No @Transactional method returns a stream")
  void noTransactionalStreams() {
    FrozenRules.check(CodeRules.noTransactionalOnStreamsRule(), classes);
  }
}
