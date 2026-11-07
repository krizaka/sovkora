package com.krizaka.sovkora.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * ADR-092 §4 rule 1: {@code orazaka.business} is pure Java 21. No baseline, no ratchet — one Spring
 * import in the business module fails the build. The wiring (BusinessUseCasesAutoConfiguration)
 * lives in {@code sovkora-adapter-spring-ai}.
 */
class BusinessWithoutSpringTest {

  @Test
  void theBusinessDependsOnNoSpring() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora.business");

    noClasses()
        .that()
        .resideInAPackage("com.krizaka.sovkora.business..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.springframework..")
        .because(
            "the business module is pure Java 21; Spring wiring lives in sovkora-adapter-spring-ai")
        .check(classes);
  }
}
