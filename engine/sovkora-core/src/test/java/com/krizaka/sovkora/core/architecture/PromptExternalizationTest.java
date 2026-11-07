package com.krizaka.sovkora.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces ADR-012: All prompt textual matrices must be externalized to {@code .st} or {@code .md}
 * resource files. Engine and pipeline packs must not contain hardcoded prompt constants.
 *
 * <p>Implementation note: ArchUnit cannot inspect string literal <em>values</em> at bytecode level.
 * This test uses a heuristic — it flags {@code static final String} fields in engine/pipeline
 * classes, excluding loggers and known safe constants (class names, property keys).
 */
class PromptExternalizationTest {

  private static JavaClasses engineClasses;

  @BeforeAll
  static void importClasses() {
    engineClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora.core.application");
  }

  @Test
  @DisplayName("[ADR-012] Engine/pipeline must not contain static final String prompt constants")
  void engineMustNotContainHardcodedPromptConstants() {
    noFields()
        .that()
        .areDeclaredInClassesThat()
        .resideInAnyPackage("com.krizaka.sovkora.core.application..")
        .should(beStaticFinalStringExcludingSafePatterns())
        .because(
            "All prompt text blocks must be externalized to .st resource files [ADR-012]."
                + " Static final String fields suggest hardcoded prompts.")
        .check(engineClasses);
  }

  private static ArchCondition<JavaField> beStaticFinalStringExcludingSafePatterns() {
    return new ArchCondition<>("be a static final String prompt constant") {
      @Override
      public void check(JavaField field, ConditionEvents events) {
        int modifiers = field.reflect().getModifiers();
        boolean isStaticFinal = Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers);
        boolean isString = field.getRawType().isEquivalentTo(String.class);

        if (!isStaticFinal || !isString) {
          return;
        }

        String name = field.getName();
        // Allow logger constants, property key prefixes, and short identifiers
        if ("logger".equals(name)
            || name.startsWith("LOG")
            || name.startsWith("PROPERTY_")
            || name.startsWith("CONFIG_")
            || "DEFAULT_PROVIDER".equals(name)
            || name.contains("KEY")
            || name.contains("PREFIX")
            || name.contains("HEADER")) {
          return;
        }

        String message =
            String.format(
                "Field '%s' in %s is a static final String — "
                    + "potential hardcoded prompt violating ADR-012",
                field.getName(), field.getOwner().getName());
        events.add(SimpleConditionEvent.violated(field, message));
      }
    };
  }
}
