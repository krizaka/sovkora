package com.krizaka.sovkora.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces ADR-009: Fields in concrete core classes must be strictly private. Exemptions:
 *
 * <ul>
 *   <li>Records — components are implicitly private final
 *   <li>Enums — enum constants are public static final by design
 *   <li>Abstract sealed classes — package-private fields shared with single permitted subclass
 * </ul>
 */
class EncapsulationTest {

  private static JavaClasses coreClasses;

  @BeforeAll
  static void importClasses() {
    coreClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora.core");
  }

  @Test
  @DisplayName("[ADR-009] All fields in concrete core classes must be private")
  void allFieldsInConcreteClassesMustBePrivate() {
    fields()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("com.krizaka.sovkora.core..")
        .and()
        .areDeclaredInClassesThat()
        .areNotRecords()
        .and()
        .areDeclaredInClassesThat()
        .areNotEnums()
        .and()
        .areDeclaredInClassesThat()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should()
        .bePrivate()
        .because(
            "Fields must be strictly private and immutable after constructor seals [ADR-009]."
                + " Records, enums, and sealed abstract base classes are exempt.")
        .check(coreClasses);
  }
}
