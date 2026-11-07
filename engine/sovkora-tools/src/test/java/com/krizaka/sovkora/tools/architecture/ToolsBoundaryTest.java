package com.krizaka.sovkora.tools.architecture;

import static com.krizaka.sovkora.test.TestConstants.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit boundary enforcement for the sovkora-tools module.
 *
 * <p>Validates that tools remain identity-blind and router-blind, communicating only through
 * core-defined ports (ERR-102, §1.A). Tools may implement interfaces defined in {@code
 * sovkora-core.pipeline.*} but must never import from identity or router.
 */
class ToolsBoundaryTest {

  private static JavaClasses toolsClasses;

  @BeforeAll
  static void importClasses() {
    toolsClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora.tools");
  }

  @Test
  @DisplayName("[ERR-102] Tools must not depend on identity")
  void toolsAreIdentityBlind() {
    noClasses()
        .that()
        .resideInAPackage(PKG_TOOLS)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.krizaka.users..")
        .because("Tools are identity-blind — absolute ban on importing identity types [ERR-102]")
        .check(toolsClasses);
  }

  @Test
  @DisplayName("[ERR-102] Tools must not depend on router")
  void toolsAreRouterBlind() {
    noClasses()
        .that()
        .resideInAPackage(PKG_TOOLS)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.krizaka.sovkora.conversationservice..")
        .because("Tools are router-blind — absolute ban on importing router types [ERR-102]")
        .check(toolsClasses);
  }

  @Test
  @DisplayName("[ERR-106] No field injection in tools classes")
  void noFieldInjectionInTools() {
    noFields()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage(PKG_TOOLS)
        .should()
        .beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
        .because(
            "Field injection is prohibited — constructor-based DI is mandatory [ADR-012, ERR-106]")
        .check(toolsClasses);
  }

  @Test
  @DisplayName(
      "[ERR-109] Persistence components must reside strictly under infrastructure.adapter.persistence package hierarchy")
  void persistenceComponentsPackageHygiene() {
    classes()
        .that()
        .implement(jakarta.persistence.AttributeConverter.class)
        .should()
        .resideInAPackage("..infrastructure.adapter.persistence.converter..")
        .allowEmptyShould(true)
        .because(
            "JPA AttributeConverters must live in the infrastructure.adapter.persistence.converter package")
        .check(toolsClasses);

    classes()
        .that()
        .areAnnotatedWith(jakarta.persistence.Entity.class)
        .should()
        .resideInAPackage("..infrastructure.adapter.persistence.entity..")
        .because(
            "JPA Entity classes must live in the infrastructure.adapter.persistence.entity package")
        .check(toolsClasses);

    classes()
        .that()
        .areAssignableTo(org.springframework.data.repository.Repository.class)
        .should()
        .resideInAPackage("..infrastructure.adapter.persistence.repository..")
        .because(
            "Spring Data Repositories must live in the infrastructure.adapter.persistence.repository package")
        .check(toolsClasses);
  }

  @Test
  @DisplayName("[ERR-109] Entity packs must not depend on outer service layers")
  void entitiesDoNotDependOnOuterLayers() {
    noClasses()
        .that()
        .resideInAPackage("..infrastructure.adapter.persistence.entity..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.krizaka.sovkora.tools.application.service..",
            "com.krizaka.sovkora.conversationservice..")
        .because("Entities must not import or depend on outer service or router layers [ERR-109]")
        .check(toolsClasses);
  }

  @Test
  @DisplayName("[BILL-001] the billing contract is reached only through the CALL meters")
  void billingOnlyThroughTheMeters() {
    noClasses()
        .that()
        .resideInAPackage(PKG_TOOLS)
        .and()
        .resideOutsideOfPackages(
            "com.krizaka.sovkora.tools.infrastructure.adapter.billing..",
            "com.krizaka.sovkora.tools.infrastructure.config..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.krizaka.billing..")
        .because(
            "a service meters a call through its port (ResearchMeter, CheckMeter); only their"
                + " credit-hold adapters speak the billing contract, so no call is held, settled"
                + " or released twice or by a second protocol (ADR-033)")
        .check(toolsClasses);
  }

  @Test
  @DisplayName("[BILL-002] the CALL meters never log")
  void metersNeverLog() {
    noClasses()
        .that()
        .resideInAPackage("com.krizaka.sovkora.tools.infrastructure.adapter.billing..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.slf4j..", "java.util.logging..", "org.apache.commons.logging..")
        .orShould()
        .dependOnClassesThat()
        .belongToAnyOf(System.Logger.class)
        .because(
            "a meter holds who is billed and which run it is for; the ledger is the record of a"
                + " hold, a log line of its arguments is a copy of them nobody audits")
        .check(toolsClasses);
  }
}
