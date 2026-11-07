package com.krizaka.sovkora.interceptor.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.interceptor.validation.ClosedLoopValidationInterceptor;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-092 / sovkora-engine#116: the control loop sits on the path that decides what is served
 * ({@code screenResponse}); a {@code postProcess} only observes an answer already served, so the
 * control must not have one.
 */
class ControlLoopRuleTest {

  private static final com.tngtech.archunit.core.domain.JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importClasses(ClosedLoopValidationInterceptor.class);

  @Test
  @DisplayName("the closed-loop control decides in screenResponse, never in a postProcess")
  void controlDecidesBeforeServing() {
    assertThat(
            Arrays.stream(ClosedLoopValidationInterceptor.class.getDeclaredMethods())
                .map(Method::getName))
        .as("an answer already served cannot be stopped (ADR-092 control loop)")
        .doesNotContain("postProcess");
    methods()
        .that()
        .areDeclaredIn(ClosedLoopValidationInterceptor.class)
        .and()
        .haveName("screenResponse")
        .should()
        .bePublic()
        .check(CLASSES);
  }
}
