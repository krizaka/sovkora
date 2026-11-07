package com.krizaka.sovkora.core.architecture;

import static com.krizaka.sovkora.test.TestConstants.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.krizaka.sovkora.test.architecture.LoggedContentRules;
import com.krizaka.sovkora.test.architecture.SourceFileScanner;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces constructor immutability and code craftsmanship governance rules across sovkora-core.
 *
 * <p>ADR-007, ADR-008: Collection fields (List, Map, Set) in non-record domain components must be
 * declared {@code private final} and initialized via immutable copying mechanisms.
 */
class GovernanceTest {

  /** Production-only classes (used by ADR-007, GOV-001..003 rules). */
  private static JavaClasses coreClasses;

  /** All core classes including test layer (used by ERR-103 file isolation rule). */
  private static JavaClasses allCoreClasses;

  @BeforeAll
  static void importClasses() {
    coreClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora.core");
    allCoreClasses = new ClassFileImporter().importPackages("com.krizaka.sovkora.core");
  }

  @Test
  @DisplayName("[ADR-007] Collection fields in non-record classes must be private final")
  void collectionFieldsMustBePrivateFinal() {
    fields()
        .that()
        .haveRawType(List.class)
        .or()
        .haveRawType(Map.class)
        .or()
        .haveRawType(Set.class)
        .and()
        .areDeclaredInClassesThat()
        .areNotRecords()
        .and()
        .areDeclaredInClassesThat()
        .areNotEnums()
        .and()
        .areDeclaredInClassesThat()
        .areNotInterfaces()
        .and()
        .areDeclaredInClassesThat()
        .haveSimpleNameNotContaining("Abstract")
        .should(bePrivateAndFinal())
        .because(
            "Collection fields must be private final for immutability [ADR-007, ADR-008]."
                + " Use Map.copyOf()/List.copyOf() in constructors.")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-129] application/service holds only service impls (*Impl or Default*)")
  void applicationServiceHoldsOnlyImpls() {
    classes()
        .that()
        .resideInAPackage("com.krizaka.sovkora.core.application.service..")
        .and()
        .areTopLevelClasses()
        .should()
        .haveSimpleNameEndingWith("Impl")
        .orShould()
        .haveSimpleNameStartingWith("Default")
        .because(
            "application/service holds only inbound-port implementations (*ServiceImpl, or"
                + " Default* when public and registered by an adapter's auto-configuration, plus"
                + " stereotype impls). Mappers are inlined into"
                + " their impl; factories live with the concern they build (routing); never here"
                + " [ERR-129/130].")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-130] infrastructure/support holds only cross-cutting helpers")
  void supportPackageHoldsOnlyHelpers() {
    noClasses()
        .that()
        .resideInAPackage("com.krizaka.sovkora.core.infrastructure.support..")
        .should()
        .haveSimpleNameEndingWith("Service")
        .orShould()
        .haveSimpleNameEndingWith("Controller")
        .orShould()
        .haveSimpleNameEndingWith("Adapter")
        .because(
            "infrastructure/support holds cross-cutting helpers (*Util) and shared *Exceptions"
                + " only — never a *Service/*Controller/*Adapter [ERR-130].")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[GOV-001] No anonymous classes in production source (enums/TypeRef exempt)")
  void noAnonymousClassesInProduction() {
    classes()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .notBeAnonymousClasses()
        .orShould(beEnumConstantOrTypeReference())
        .because(
            "Anonymous classes are banned in production — use explicit named implementations"
                + " or lambda expressions [Governance Mandate]."
                + " Enum constants and Jackson TypeReferences are exempt.")
        .check(coreClasses);
  }

  private static ArchCondition<com.tngtech.archunit.core.domain.JavaClass>
      beEnumConstantOrTypeReference() {
    return new ArchCondition<>("be an enum constant body or TypeReference") {
      @Override
      public void check(
          com.tngtech.archunit.core.domain.JavaClass javaClass, ConditionEvents events) {
        boolean isEnumBody =
            javaClass.getEnclosingClass().isPresent()
                && javaClass.getEnclosingClass().get().isEnum();
        boolean isTypeRef =
            javaClass.getSuperclass().isPresent()
                && javaClass.getSuperclass().get().getName().contains("TypeReference");
        if (!isEnumBody && !isTypeRef) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass, "Anonymous class <" + javaClass.getName() + "> is not exempt"));
        }
      }
    };
  }

  @Test
  @DisplayName(
      "[GOV-004] No classes in production should access standard streams (System.out/System.err)")
  void noStandardStreams() {
    com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
        .because("Use SLF4J loggers instead of standard output/error streams")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[GOV-002] Engine integration gates must be public (EngineModelRegistry exempt)")
  void integrationGatesMustBePublic() {
    classes()
        .that()
        .resideInAPackage("com.krizaka.sovkora.core.application.engine..")
        .and()
        .haveSimpleNameEndingWith("Engine")
        .should()
        .bePublic()
        .because(
            "Engine integration gates (Engine, AbstractEngine) must be public."
                + " EngineModelRegistry is package-private per ADR-009.")
        .check(coreClasses);
  }

  @Test
  @DisplayName(
      "[GOV-003] No hardcoded localhost, dummy keys, or System.getenv in production source")
  void noHardcodedLocalhostInProductionSource() throws IOException {
    Path sourceRoot = Path.of("sovkora-core/src/main/java");
    if (!Files.exists(sourceRoot)) {
      sourceRoot = Path.of("src/main/java");
    }

    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(sourceRoot)) {
      paths
          .filter(p -> p.toString().endsWith(".java"))
          .forEach(
              path -> {
                try {
                  List<String> lines = Files.readAllLines(path);
                  for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    // Skip comments and javadoc
                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                      continue;
                    }
                    if (line.contains("localhost:") || line.contains("127.0.0.1")) {
                      violations.add(path.getFileName() + ":" + (i + 1) + " -> " + line);
                    }
                    if (line.contains("dummy-key") || line.contains("\"dummy")) {
                      violations.add(
                          path.getFileName() + ":" + (i + 1) + " -> hardcoded credential: " + line);
                    }
                    if (line.contains("System.getenv(")) {
                      violations.add(
                          path.getFileName()
                              + ":"
                              + (i + 1)
                              + " -> raw env access (use typed CoreProperties): "
                              + line);
                    }
                  }
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }

    assertTrue(
        violations.isEmpty(),
        "Production source contains banned literals:\n" + String.join("\n", violations));
  }

  @Test
  @DisplayName(
      "[ERR-103] Every top-level class (production + test) must reside in a dedicated file")
  void oneTopLevelClassPerFile() {
    classes()
        .that()
        .resideInAPackage(PKG_CORE)
        .and()
        .doNotHaveModifier(com.tngtech.archunit.core.domain.JavaModifier.SYNTHETIC)
        .should(
            new ArchCondition<com.tngtech.archunit.core.domain.JavaClass>(
                "reside in a dedicated file matching their class name") {
              @Override
              public void check(
                  com.tngtech.archunit.core.domain.JavaClass javaClass, ConditionEvents events) {
                // Bypass nested, inner, member, or anonymous classes
                if (javaClass.isAnonymousClass() || javaClass.isMemberClass()) {
                  return;
                }
                String sourceFileName =
                    javaClass.getSource().isPresent()
                        ? javaClass.getSource().get().getFileName().orElse(null)
                        : null;
                if (sourceFileName != null && !"Unknown Source".equals(sourceFileName)) {
                  String expectedFileName = javaClass.getSimpleName() + ".java";
                  if (!sourceFileName.equals(expectedFileName)) {
                    String message =
                        String.format(
                            "Architecture Violation [ERR-103]: Class '%s' is illegally bundled"
                                + " inside '%s'. Every top-level component (production or test)"
                                + " must have its own dedicated file.",
                            javaClass.getFullName(), sourceFileName);
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                  }
                }
              }
            })
        .because(
            "Every top-level Java class, interface, enum, or record—whether in production"
                + " (src/main) or test suites (src/test)—must reside in its own dedicated"
                + " .java source file [ERR-103, Source File Isolation Invariant].")
        .check(allCoreClasses);
  }

  private static ArchCondition<JavaField> bePrivateAndFinal() {
    return new ArchCondition<>("be private and final") {
      @Override
      public void check(JavaField field, ConditionEvents events) {
        boolean isPrivate =
            field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE);
        boolean isFinal =
            field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.FINAL);
        if (!isPrivate || !isFinal) {
          String message =
              String.format(
                  "Field <%s> in <%s> is not private final (private=%s, final=%s)",
                  field.getName(), field.getOwner().getName(), isPrivate, isFinal);
          events.add(SimpleConditionEvent.violated(field, message));
        }
      }
    };
  }

  @Test
  @DisplayName("[ERR-104] No class names with redundant 'Sovkora' prefix")
  void noRedundantProjectPrefix() {
    classes()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .haveSimpleNameNotStartingWith("Sovkora")
        .because(
            "The project name must not be prepended to class names [ERR-104]."
                + " Use package topology for ownership.")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-112] No @RestController or @Controller annotations in core")
  void noWebControllersInCore() {
    classes()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .notBeAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
        .andShould()
        .notBeAnnotatedWith(org.springframework.stereotype.Controller.class)
        .because(
            "sovkora-core is a stateless library — web controllers belong in sovkora-router"
                + " [ERR-112, Section 1.A]")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-112+] No classes in core may depend on web MVC annotation/servlet packs")
  void noWebMvcAnnotationDependenciesInCore() {
    com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.springframework.web.bind.annotation..")
        .because(
            "sovkora-core must never import web MVC annotations (@RequestMapping, @GetMapping,"
                + " @PostMapping, @ResponseBody, etc.). Web binding is the exclusive domain of"
                + " sovkora-router [§1.2 Stateless Core, ERR-112]."
                + " ArchUnit enforces this as a non-negotiable compile-time gatekeeper.")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-112++] No classes in core may depend on javax/jakarta.servlet packs")
  void noServletDependenciesInCore() {
    com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("jakarta.servlet..")
        .because(
            "sovkora-core must remain servlet-agnostic. Servlet APIs belong exclusively"
                + " in sovkora-router [§1.2 Stateless Core]."
                + " ArchUnit enforces this as a non-negotiable compile-time gatekeeper.")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ADR-009] Fields in concrete non-record classes must be private")
  void fieldsInConcreteClassesMustBePrivate() {
    fields()
        .that()
        .areDeclaredInClassesThat()
        .areNotRecords()
        .and()
        .areDeclaredInClassesThat()
        .areNotEnums()
        .and()
        .areDeclaredInClassesThat()
        .areNotInterfaces()
        .and()
        .areDeclaredInClassesThat()
        .haveSimpleNameNotContaining("Abstract")
        .and()
        .areNotStatic()
        .should(
            new ArchCondition<JavaField>("be private") {
              @Override
              public void check(JavaField field, ConditionEvents events) {
                boolean isPrivate =
                    field
                        .getModifiers()
                        .contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE);
                if (!isPrivate) {
                  events.add(
                      SimpleConditionEvent.violated(
                          field,
                          String.format(
                              "Field <%s> in <%s> must be private [ADR-009]",
                              field.getName(), field.getOwner().getName())));
                }
              }
            })
        .because(
            "Instance fields in concrete classes must be private [ADR-009]."
                + " Use accessor methods for encapsulation.")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-105] *Impl classes must be package-private (not public)")
  void implClassesMustBePackagePrivate() {
    classes()
        .that()
        .resideInAPackage(PKG_CORE)
        .and()
        .haveSimpleNameEndingWith("Impl")
        .should()
        .notBePublic()
        .because(
            "Implementation classes (*Impl) must be package-private."
                + " Cross-module interaction happens through interfaces only [ERR-105].")
        .allowEmptyShould(true)
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-107] Mapper classes must be final with only static methods")
  void mappersAreStaticUtilities() {
    classes()
        .that()
        .resideInAPackage(PKG_CORE)
        .and()
        .haveSimpleNameEndingWith("Mapper")
        .should()
        .haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
        .because(
            "Mapper classes must be final static utility classes."
                + " Field-by-field mapping in controllers/services is banned [ERR-107].")
        // The core holds no mapper since JsonChunkMapper moved to the Spring AI adapter (#120):
        // the rule stays for the next one, and the adapter's own test below keeps it non-vacuous.
        .allowEmptyShould(true)
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-113] No Environment injection in production beans")
  void noEnvironmentInjection() {
    // Was a second implementation of the shared rule, written inline here, while the shared
    // one was
    // invoked by no suite at all (GOV-006, §4.3). One author now.
    SourceFileScanner.assertNoEnvironmentInjection(Path.of("src", "main", "java"));
  }

  @Test
  @DisplayName("[GOV-005] No @Autowired field injection — constructor DI only")
  void noFieldInjection() {
    fields()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage(PKG_CORE)
        .and()
        .areAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
        .should(
            new ArchCondition<JavaField>("not exist (field injection is banned)") {
              @Override
              public void check(JavaField field, ConditionEvents events) {
                events.add(
                    SimpleConditionEvent.violated(
                        field,
                        String.format(
                            "Field <%s> in <%s> uses @Autowired field injection."
                                + " Use constructor injection instead [GOV-005].",
                            field.getName(), field.getOwner().getName())));
              }
            })
        .allowEmptyShould(true)
        .because(
            "Constructor injection is mandatory. @Autowired field injection is banned [GOV-005].")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-118] No classes in core may depend on Spring AMQP packs")
  void noAmqpDependenciesInCore() {
    com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("org.springframework.amqp..")
        .because(
            "sovkora-core must remain AMQP-agnostic. RabbitMQ communication flows through"
                + " the EventPublisher outbound port interface. Concrete AMQP adapters belong"
                + " exclusively in sovkora-persistence/app [§2.14, ERR-118]."
                + " ArchUnit enforces this as a non-negotiable compile-time gatekeeper.")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-118+] No classes in core may depend on RabbitMQ client packs")
  void noRabbitMqDependenciesInCore() {
    com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage(PKG_CORE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.rabbitmq..")
        .because(
            "sovkora-core must never import RabbitMQ client types directly."
                + " All messaging infrastructure is encapsulated behind the EventPublisher"
                + " outbound port [§2.14, ERR-118]."
                + " ArchUnit enforces this as a non-negotiable compile-time gatekeeper.")
        .check(coreClasses);
  }

  @Test
  @DisplayName(
      "[ERR-122] Only PromptContextInterceptor interface is permitted in"
          + " application.interceptor package")
  void onlyPortInterfaceInInterceptorPackage() {
    classes()
        .that()
        .resideInAPackage("com.krizaka.sovkora.core.application.interceptor..")
        .should()
        .beInterfaces()
        .because(
            "The application.interceptor package is a pure SPI port layer."
                + " Only the PromptContextInterceptor interface is permitted here."
                + " All concrete interceptor implementations must reside in standalone"
                + " sovkora-interceptor-* submodules [ERR-122, §1.4].")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[ERR-122+] No concrete PromptContextInterceptor implementations in core")
  void noConcreteInterceptorImplementationsInCore() {
    classes()
        .that()
        .resideInAPackage(PKG_CORE)
        .and()
        .implement(com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor.class)
        .should(
            new ArchCondition<com.tngtech.archunit.core.domain.JavaClass>(
                "not exist — zero concrete interceptors permitted in core") {
              @Override
              public void check(
                  com.tngtech.archunit.core.domain.JavaClass javaClass, ConditionEvents events) {
                events.add(
                    SimpleConditionEvent.violated(
                        javaClass,
                        String.format(
                            "Architecture Violation [ERR-122]: Class '%s' implements"
                                + " PromptContextInterceptor inside sovkora-core."
                                + " Concrete interceptors must reside in"
                                + " sovkora-interceptor-* submodules.",
                            javaClass.getFullName())));
              }
            })
        .allowEmptyShould(true)
        .because(
            "sovkora-core is a pure domain hexagon. Zero concrete PromptContextInterceptor"
                + " implementations are permitted [ERR-122, §1.4]."
                + " All interceptors must reside in standalone Maven submodules under"
                + " sovkora-interceptors/ and be loaded via AutoConfiguration.imports.")
        .check(coreClasses);
  }

  /**
   * Foundational engine prompt templates permitted in {@code
   * sovkora-core/src/main/resources/prompts/}.
   *
   * <p>Any file not in this allow-list is a resource isolation violation. Domain-specific, RAG,
   * memory, or translation prompt templates must reside in their respective {@code
   * sovkora-interceptor-*} submodule's {@code src/main/resources/} directory.
   */
  private static final Set<String> ALLOWED_CORE_PROMPT_FILES =
      Set.of("context-envelope.st", "system-refinement.st", "system-router.st");

  @Test
  @DisplayName("[ERR-125] Core prompts/ directory must contain only foundational engine templates")
  void corePromptsDirectoryContainsOnlyFoundationalTemplates() throws IOException {
    // Resolve the sovkora-core module root relative to test execution directory
    Path moduleRoot = Path.of(System.getProperty("user.dir"));
    Path promptsDir = moduleRoot.resolve("src/main/resources/prompts");

    assertTrue(
        Files.isDirectory(promptsDir), "Expected prompts directory to exist at: " + promptsDir);

    List<String> violations = new ArrayList<>();

    try (Stream<Path> entries = Files.list(promptsDir)) {
      entries
          .filter(Files::isRegularFile)
          .forEach(
              file -> {
                String fileName = file.getFileName().toString();
                if (!ALLOWED_CORE_PROMPT_FILES.contains(fileName)) {
                  violations.add(
                      String.format(
                          "Architecture Violation [ERR-125]: Unauthorized prompt template '%s'"
                              + " detected in sovkora-core/src/main/resources/prompts/."
                              + " Domain-specific prompt files must reside in their respective"
                              + " sovkora-interceptor-* submodule's src/main/resources/ directory.",
                          fileName));
                }
              });
    }

    assertTrue(
        violations.isEmpty(),
        "[ERR-125] Resource Isolation Violations:\n" + String.join("\n", violations));
  }

  @Test
  @DisplayName("[ERR-135] Records must be pure, stateless Data Carriers")
  void recordsMustBePureDataCarriers() {
    com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
        .that()
        .areRecords()
        .should()
        .dependOnClassesThat(
            new com.tngtech.archunit.base.DescribedPredicate<
                com.tngtech.archunit.core.domain.JavaClass>("be banned record dependencies") {
              @Override
              public boolean test(com.tngtech.archunit.core.domain.JavaClass javaClass) {
                String pkg = javaClass.getPackageName();
                String name = javaClass.getSimpleName();

                // Banned loggers
                if (pkg.startsWith("org.slf4j") || pkg.startsWith("java.util.logging")) {
                  return true;
                }

                // Banned active I/O
                if (pkg.startsWith("java.nio.file")) {
                  return true;
                }
                if (pkg.startsWith("java.io")
                    && !"java.io.Serializable".equals(javaClass.getName())) {
                  return true;
                }

                // Banned Spring bean/context/stereotype
                if (pkg.startsWith("org.springframework.stereotype")
                    || pkg.startsWith("org.springframework.context")
                    || pkg.startsWith("org.springframework.beans")) {
                  return true;
                }
                return (name.endsWith("Client")
                        || name.endsWith("Service")
                        || name.endsWith("Facade")
                        || name.endsWith("Manager"))
                    && !pkg.startsWith("java.")
                    && !pkg.startsWith("javax.")
                    && !pkg.startsWith("jakarta.");
              }
            })
        .because(
            "Java Records are stateless Data Carriers and must remain pure [ERR-135]. "
                + "They cannot perform I/O, log, or depend on Spring context/beans/AiClient.")
        .check(coreClasses);
  }

  @Test
  @DisplayName("[LOG-001] no logging call takes a prompt, a response body or a message text")
  void noLoggingCallTakesContent() {
    LoggedContentRules.assertNoLoggingCallTakesContent();
  }
}
