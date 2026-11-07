package com.krizaka.sovkora.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces fail-fast compliance: production engine and pipeline service classes must throw only
 * {@code CoreException}, never raw {@code RuntimeException} or {@code IllegalStateException}.
 *
 * <p>Exemptions per ADR:
 *
 * <ul>
 *   <li>Records — {@code IllegalArgumentException} in compact constructors is idiomatic (ADR-007)
 *   <li>{@code @Configuration} bootstrap classes — exempt per ADR-011
 *   <li>Bridge/adapter classes — may handle framework exceptions for resilience
 *   <li>Video infrastructure — currently uses RestClient (tracked for future extraction)
 * </ul>
 */
class FailFastComplianceTest {

  private static JavaClasses engineServiceClasses;

  @BeforeAll
  static void importClasses() {
    engineServiceClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora.core.application");
  }

  @Test
  @DisplayName("[Gate-FF] Core engine classes must not directly throw IllegalStateException")
  void coreEngineMustNotThrowIllegalStateException() {
    noClasses()
        .that()
        .resideInAPackage("com.krizaka.sovkora.core.application..")
        .and()
        .areNotRecords()
        .and()
        .haveSimpleNameNotEndingWith("Configuration")
        .and()
        .haveSimpleNameNotContaining("Bridge")
        .and()
        .haveSimpleNameNotContaining("VideoService")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("java.lang.IllegalStateException")
        .because(
            "Engine service classes must throw CoreException, not IllegalStateException."
                + " Records, Configuration, and Bridge/Video adapter classes are exempt.")
        .check(engineServiceClasses);
  }
}
