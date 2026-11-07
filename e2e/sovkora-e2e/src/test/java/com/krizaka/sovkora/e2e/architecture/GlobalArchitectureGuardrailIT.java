package com.krizaka.sovkora.e2e.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * Sovkora Enterprise Hexagonal Architecture Constitution. Enforces strict DDD boundaries, absolute
 * Domain purity, port/adapter naming conventions, and prevents any framework ecosystem leakage into
 * the core.
 */
@AnalyzeClasses(packages = "com.krizaka.sovkora")
class GlobalArchitectureGuardrailIT {

  // =========================================================================
  // 1. THE HEXAGONAL MATRIX (LAYERED BOUNDARIES)
  // =========================================================================

  @ArchTest
  static final ArchRule enforce_strict_hexagonal_architecture =
      layeredArchitecture()
          .consideringAllDependencies()

          // Define the architectural rings from the outside in
          .layer("Inbound_Adapters")
          .definedBy("..gateway..", "..controller..", "..cli..", "..graphql..")
          .layer("Outbound_Adapters")
          .definedBy("..persistence..", "..repository.impl..", "..infrastructure.tools..")
          .layer("Interceptors")
          .definedBy("..interceptor..")
          .layer("Application_Ports_And_Services")
          .definedBy("..application..", "..usecase..", "..port..", "..business..")
          .layer("Domain_Core")
          .definedBy("..core.domain..", "..business.domain..", "..domain.model..")

          // Execution Regulations (Dependency Inversion Rule Enforcement)
          .whereLayer("Domain_Core")
          .mayNotAccessAnyLayer()
          .whereLayer("Application_Ports_And_Services")
          .mayOnlyAccessLayers("Domain_Core")
          .whereLayer("Interceptors")
          .mayOnlyAccessLayers("Application_Ports_And_Services", "Domain_Core")
          .whereLayer("Inbound_Adapters")
          .mayOnlyAccessLayers("Application_Ports_And_Services", "Interceptors", "Domain_Core")
          .whereLayer("Outbound_Adapters")
          .mayOnlyAccessLayers("Application_Ports_And_Services", "Domain_Core");

  // =========================================================================
  // 2. DOMAIN PURITY GUARDIANS (ZERO FRAMEWORK LEAKAGE)
  // =========================================================================

  @ArchTest
  static final ArchRule domain_core_must_remain_framework_agnostic =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..")
          .orShould()
          .dependOnClassesThat()
          .resideInAPackage("jakarta.persistence..")
          .orShould()
          .dependOnClassesThat()
          .resideInAPackage("hibernate..")
          .orShould()
          .dependOnClassesThat()
          .resideInAPackage("com.fasterxml.jackson..")
          .as(
              "The Domain Core must be pure Java. External frameworks, database annotations (JPA), or parsers are strictly forbidden.");

  @ArchTest
  static final ArchRule domain_models_must_not_carry_spring_annotations =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .beAnnotatedWith("org.springframework.stereotype.Component")
          .orShould()
          .beAnnotatedWith("org.springframework.stereotype.Service")
          .orShould()
          .beAnnotatedWith("org.springframework.stereotype.Repository")
          .as(
              "Domain entities and aggregates must be instantiated manually or via factories. Do not pollute them with Spring Beans metadata.");

  // =========================================================================
  // 3. NOMENCLATURE & DESIGN PATTERNS CONVENTIONS
  // =========================================================================

  @ArchTest
  static final ArchRule inbound_adapters_naming_convention =
      classes()
          .that()
          .resideInAPackage("..gateway..")
          .or()
          .resideInAPackage("..controller..")
          .should()
          .haveSimpleNameEndingWith("Controller")
          .orShould()
          .haveSimpleNameEndingWith("Gateway")
          .orShould()
          .haveSimpleNameEndingWith("Consumer")
          .as(
              "Inbound structural entrypoints must explicitly follow the nomenclature: *Controller, *Gateway, or *Consumer.");

  @ArchTest
  static final ArchRule application_usecases_naming_convention =
      classes()
          .that()
          .resideInAPackage("..usecase..")
          .or()
          .resideInAPackage("..application..")
          .and()
          .areInterfaces()
          .should()
          .haveSimpleNameEndingWith("UseCase")
          .orShould()
          .haveSimpleNameEndingWith("Port")
          .orShould()
          .haveSimpleNameEndingWith("Service")
          .as("Application boundary contracts must end with *UseCase, *Port, or *Service.");

  @ArchTest
  static final ArchRule outbound_adapters_should_implement_ports =
      classes()
          .that()
          .resideInAPackage("..persistence..")
          .and()
          .areNotInterfaces()
          .should()
          .haveSimpleNameEndingWith("Adapter")
          .orShould()
          .haveSimpleNameEndingWith("Repository")
          .as(
              "Outbound infrastructure implementations must be explicitly named *Adapter or *Repository.");

  // =========================================================================
  // 4. CODE CLEANLINESS & ISOLATION SENSORS
  // =========================================================================

  @ArchTest
  static final ArchRule identity_tier3_impl_is_owned_only_by_identity_service =
      noClasses()
          .that()
          .resideOutsideOfPackages("com.krizaka.users..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.krizaka.users.application..",
              "com.krizaka.users.infrastructure..",
              "com.krizaka.users.domain.ports..")
          .as(
              "[SEAM-002] Only krizaka-users-service owns the identity Tier-3 impl. Every other"
                  + " bounded context reaches identity through the krizaka-users-api contract"
                  + " (com.krizaka.users.domain.{model,exception}), never the implementation.");

  @ArchTest
  static final ArchRule enforce_constructor_injection_only =
      GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.as(
          "Field injection via @Autowired or @Inject is an anti-pattern. Use strict final constructor injection for compile-time safety.");

  @ArchTest
  static final ArchRule cyclical_dependency_breaker =
      slices()
          .matching("com.krizaka.sovkora.(*)..")
          .should()
          .beFreeOfCycles()
          .as(
              "The Monorepo package dependency structure must remain a strict Directed Acyclic Graph (DAG) to prevent build locks.");

  // =========================================================================
  // 5. ZERO-MOCKING POLICY (E2E INFRASTRUCTURE PURITY)
  // =========================================================================

  @ArchTest
  static final ArchRule no_mocking_allowed_in_e2e =
      noClasses()
          .that()
          .resideInAPackage("com.krizaka.sovkora.e2e..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.mockito..")
          .orShould()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework.boot.test.mock..")
          .as("Tier 3 E2E must use real infrastructure. Mocking is prohibited.");

  @ArchTest
  static final ArchRule no_spring_boot_test_in_e2e =
      noClasses()
          .that()
          .resideInAPackage("com.krizaka.sovkora.e2e..")
          .should()
          .beAnnotatedWith("org.springframework.boot.test.context.SpringBootTest")
          .as(
              "E2E tests must remain clean black-box client runners. @SpringBootTest is forbidden — use Playwright + raw JDBC/Lettuce/AMQP clients only.");

  // =========================================================================
  // 6. MODULE ISOLATION SENSORS
  // =========================================================================

  @ArchTest
  static final ArchRule tools_must_not_import_identity =
      noClasses()
          .that()
          .resideInAPackage("com.krizaka.sovkora.engine.tools..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.krizaka.users..")
          .as(
              "sovkora-engine-tools must never import krizaka-users-core (ERR-102). Pass String userId from gateway.");

  @ArchTest
  static final ArchRule business_must_not_import_conversation_infrastructure =
      noClasses()
          .that()
          .resideInAPackage("com.krizaka.sovkora.engine.business..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.krizaka.sovkora.conversation.infrastructure.client..")
          .as(
              "sovkora-engine-business must have absolutely zero compile dependencies on classes located inside com.krizaka.sovkora.conversation.infrastructure.client.");

  @ArchTest
  static final ArchRule interceptors_naming_convention =
      classes()
          .that()
          .resideInAPackage("..interceptor..")
          .and()
          .areNotInterfaces()
          .and()
          .areNotEnums()
          .should()
          .haveSimpleNameEndingWith("Interceptor")
          .orShould()
          .haveSimpleNameEndingWith("Advisor")
          .orShould()
          .haveSimpleNameEndingWith("Resolver")
          .orShould()
          .haveSimpleNameEndingWith("Injector")
          .as(
              "Interceptor implementations must follow the naming convention: *Interceptor, *Advisor, *Resolver, or *Injector.");
}
