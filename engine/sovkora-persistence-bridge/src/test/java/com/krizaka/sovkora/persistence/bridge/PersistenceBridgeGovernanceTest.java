package com.krizaka.sovkora.persistence.bridge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.test.architecture.GovernanceRules;
import com.krizaka.sovkora.test.architecture.PackPurityRules;
import com.krizaka.sovkora.test.architecture.SourceFileScanner;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture governance for {@code sovkora-persistence-bridge}: the pack-private {@code @Service}
 * adapters that implement the core's DB-backed outbound ports by delegating to the persistence
 * inbound ports. This is a deliberately flat adapter package (no hexagonal domain/application/
 * infrastructure sub-layers), so it reuses only the layout-agnostic shared {@link GovernanceRules}
 * plus the bridge-specific naming/visibility invariants below.
 */
class PersistenceBridgeGovernanceTest {

  private static final String BASE = "com.krizaka.sovkora.persistence.bridge";

  private static final String PEPPER_SETTING = "checks." + "subject-pepper";

  private final JavaClasses classes = GovernanceRules.importProductionClasses(BASE);

  @Test
  @DisplayName("[ERR-103] one top-level class per file")
  void oneTopLevelClassPerFile() {
    GovernanceRules.assertOneTopLevelClassPerFile(classes, BASE);
  }

  @Test
  @DisplayName("[ERR-104] no redundant Sovkora prefix")
  void noRedundantPrefix() {
    GovernanceRules.assertNoRedundantPrefix(classes, BASE);
  }

  @Test
  @DisplayName("[ERR-107] mappers are final + package-private")
  void mappersFinalAndPackagePrivate() {
    GovernanceRules.assertMappersFinal(classes, BASE);
    GovernanceRules.assertMappersPackagePrivate(classes, BASE);
  }

  @Test
  @DisplayName("[ADR-009] instance fields are private")
  void fieldsPrivate() {
    GovernanceRules.assertFieldsPrivate(classes);
  }

  @Test
  @DisplayName("[GOV-005] no @Autowired field injection")
  void noFieldInjection() {
    GovernanceRules.assertNoFieldInjection(classes, BASE);
  }

  @Test
  @DisplayName("[GOV-001] no anonymous classes")
  void noAnonymousClasses() {
    GovernanceRules.assertNoAnonymousClasses(classes, BASE);
  }

  @Test
  @DisplayName("[GOV-004] no System.out/err")
  void noStandardStreams() {
    GovernanceRules.assertNoStandardStreams(classes);
  }

  @Test
  @DisplayName("[ERR-112] no web controllers in a library")
  void noWebControllers() {
    GovernanceRules.assertNoWebControllers(classes, BASE);
  }

  @Test
  @DisplayName("[ERR-130] bridge holds only *Adapter + *Mapper — one package, one component kind")
  void onlyAdaptersAndMappers() {
    classes()
        .that()
        .resideInAPackage(BASE + "..")
        .and()
        .areTopLevelClasses()
        .should()
        .haveSimpleNameEndingWith("Adapter")
        .orShould()
        .haveSimpleNameEndingWith("Mapper")
        .because(
            "the persistence-bridge holds core outbound-port adapters (*Adapter) and their mappers"
                + " (*Mapper) only [ERR-130]")
        .check(classes);
  }

  @Test
  @DisplayName(
      "[ERR-105] adapters are package-private (wired by component scan through their port)")
  void adaptersPackagePrivate() {
    classes()
        .that()
        .resideInAPackage(BASE + "..")
        .and()
        .haveSimpleNameEndingWith("Adapter")
        .should()
        .notBePublic()
        .because(
            "bridge adapters are discovered by the host service's component scan and consumed only"
                + " through their core outbound port — never part of a public API [ERR-105]")
        .check(classes);
  }

  @Test
  @DisplayName("[ERR-113] No Environment injection in production beans")
  void noEnvironmentInjection() {
    SourceFileScanner.assertNoEnvironmentInjection(Path.of("src", "main", "java"));
  }

  /**
   * The subject pepper of the external checks is read by its adapter alone, and written by the
   * operator's sealing tool alone. A second reader of the row would hold the sealed text, or the
   * pepper, where nobody checked that it never logs, throws or prints it.
   */
  @Test
  @DisplayName("[CHK-001] the subject pepper's setting is read by its sealed adapter alone")
  void subjectPepperSettingHasOneReader() throws IOException {
    Path root = PackPurityRules.locateRepositoryRoot(Path.of(System.getProperty("user.dir")));
    Set<String> holders = new TreeSet<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file :
          files
              .filter(f -> f.toString().endsWith(".java"))
              .filter(f -> f.toString().contains("src" + File.separator + "main"))
              .toList()) {
        if (Files.readString(file).contains("\"" + PEPPER_SETTING + "\"")) {
          holders.add(file.getFileName().toString());
        }
      }
    }
    assertThat(holders)
        .as("main sources naming the setting %s", PEPPER_SETTING)
        .containsExactlyInAnyOrder(
            "SealedCheckSubjectPepperAdapter.java", "CheckSubjectPepperTool.java");
  }
}
