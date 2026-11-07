package com.krizaka.sovkora.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * ADR-092 §4 rule 1, sovkora-engine#121 (closed): {@code orazaka.core} is pure Java 21. No
 * baseline, no ratchet — one Spring import in the core fails the build. The wiring lives in {@code
 * sovkora-adapter-spring-ai}.
 */
class CoreWithoutSpringTest {

  @Test
  void theCoreDependsOnNoSpring() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora.core");

    noClasses()
        .that()
        .resideInAPackage("com.krizaka.sovkora.core..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.springframework..")
        .because("the core is pure Java 21; Spring wiring lives in sovkora-adapter-spring-ai")
        .check(classes);
  }
}
