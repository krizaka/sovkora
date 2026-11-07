package com.krizaka.sovkora.adapter.springai.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [ERR-107] kept where the mappers now live (they moved out of the core in #120). */
class MapperRuleTest {

  @Test
  @DisplayName("[ERR-107] Mapper classes must be final — and the adapter has some to check")
  void mappersAreFinal() {
    classes()
        .that()
        .resideInAPackage("com.krizaka.sovkora.adapter.springai..")
        .and()
        .haveSimpleNameEndingWith("Mapper")
        .should()
        .haveModifier(JavaModifier.FINAL)
        .because("Mapper classes must be final static utility classes [ERR-107].")
        .check(
            new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.krizaka.sovkora.adapter.springai"));
  }
}
