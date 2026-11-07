package com.krizaka.sovkora.core.architecture;

import static com.krizaka.sovkora.test.TestConstants.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces ADR-005 and Section 1.A: sovkora-core must remain 100% agnostic of router, identity, web
 * protocols, servlet infrastructure, and Spring Boot auto-configuration starters.
 */
class LayerBoundaryTest {

  private static JavaClasses coreClasses;

  @BeforeAll
  static void importClasses() {
    coreClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora.core");
  }

  @Test
  @DisplayName("[Gate-1] Core must not depend on router layer")
  void coreMustNotDependOnRouter() {
    noClasses()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.krizaka.sovkora.conversationservice..")
        .because("sovkora-core is a stateless library and must remain router-agnostic [ADR-005]")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[Gate-1] Core must not depend on identity layer")
  void coreMustNotDependOnIdentity() {
    noClasses()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.krizaka.users..")
        .because("sovkora-core must remain completely identity-blind [Section 1.A]")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[Gate-1] Core must not depend on Servlet API")
  void coreMustNotDependOnServletApi() {
    noClasses()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("jakarta.servlet..")
        .because("sovkora-core must remain HTTP/protocol agnostic [Section 1.A]")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[Gate-1] Core engine must not depend on Spring Web (infrastructure exempt)")
  void coreMustNotDependOnSpringWeb() {
    noClasses()
        .that()
        .resideInAPackage(PKG_CORE)
        .and()
        .resideOutsideOfPackage("com.krizaka.sovkora.core.infrastructure..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.springframework.web..")
        .because(
            "sovkora-core must remain web-agnostic [Section 1.A]."
                + " Infrastructure adapters (RestClient wrappers) are exempt.")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[Gate-1] Core must not depend on Spring Boot AutoConfiguration")
  void coreMustNotDependOnAutoConfiguration() {
    noClasses()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.springframework.boot.autoconfigure..")
        .because(
            "sovkora-core must not import Spring Boot starter auto-configuration [Section 1.A]")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[Gate-DI] Core classes must not use @Autowired on fields")
  void noFieldInjection() {
    noFields()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage(PKG_CORE)
        .should()
        .beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
        .because(
            "Field injection is prohibited. Constructor-based DI is mandatory [ADR-012, AGENTS.md]")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[Gate-2] Engine must never directly depend on infrastructure")
  void engineNeverTouchesInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("com.krizaka.sovkora.core.application.engine..")
        .should()
        .dependOnClassesThat(
            new DescribedPredicate<JavaClass>(
                "reside in com.krizaka.sovkora.core.infrastructure and not end with Properties") {
              @Override
              public boolean test(JavaClass input) {
                return input.getPackageName().startsWith("com.krizaka.sovkora.core.infrastructure")
                    && !input.getName().contains("Properties");
              }
            })
        .because(
            "Engine must remain media-agnostic and may only consume ingest ports [ERR-101, Section 1.A]")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[Gate-2] Engine is media-agnostic — depends only on ingest ports, not infra")
  void engineIsMediaAgnostic() {
    noClasses()
        .that()
        .resideInAPackage("com.krizaka.sovkora.core.application.engine..")
        .should()
        .dependOnClassesThat(
            new DescribedPredicate<JavaClass>(
                "reside in infrastructure or media libraries and not end with Properties") {
              @Override
              public boolean test(JavaClass input) {
                boolean isMedia =
                    input.getPackageName().startsWith("javax.sound")
                        || input.getPackageName().startsWith("java.awt.image");
                boolean isInfra =
                    input.getPackageName().startsWith("com.krizaka.sovkora.core.infrastructure")
                        && !input.getName().contains("Properties");
                return isMedia || isInfra;
              }
            })
        .because(
            "Engine must be blind to concrete infrastructure tools and media libraries [ERR-101]")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-102] Core must not depend on tools")
  void coreDoesNotDependOnTools() {
    noClasses()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.krizaka.sovkora.tools..")
        .because(
            "sovkora-core must remain tools-agnostic — reverse dependency direction prohibited [ERR-102]")
        .check(coreClasses);
  }
}
