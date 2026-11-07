package com.krizaka.sovkora.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * ADR-092 §4 rule 3, sovkora-engine#122: the core knows no pack, no studio and no product. What a
 * pack or a studio needs is a port of the core, implemented at the edge; a change to a pack lands
 * with no diff in {@code sovkora-core}. Strict, not a ratchet: the core has no such dependency
 * today and never gets one.
 */
class CoreIndependenceTest {

  @Test
  void coreDependsOnNoPackStudioOrProduct() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora");

    noClasses()
        .that()
        .resideInAPackage("com.krizaka.sovkora.core..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.krizaka.sovkora.pack..",
            "com.krizaka.sovkora.packs..",
            "com.krizaka.sovkora.studio..",
            "com.krizaka.sovkora.studios..",
            "com.krizaka.sovkora.tools..",
            "com.krizaka.sovkora.interceptor..",
            "com.krizaka.yomizaka..",
            "com.krizaka.akinaza..")
        .because(
            "the core is the engine; packs, studios and products depend on it, never the reverse")
        .check(classes);
  }
}
