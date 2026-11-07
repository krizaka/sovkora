package com.krizaka.sovkora.persistence.architecture;

import static com.krizaka.sovkora.test.architecture.GovernanceRules.*;

import com.krizaka.sovkora.test.architecture.GovernanceRules;
import com.krizaka.sovkora.test.architecture.SourceFileScanner;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Governance (naming + structural hygiene) for the sovkora-persistence (app) module, reusing the
 * shared {@link com.krizaka.sovkora.test.architecture.GovernanceRules}. Module boundaries +
 * persistence hygiene live in {@code PersistenceBoundaryTest} ([ERR-102/107/109]).
 *
 * <p>This module implements inbound persistence ports as package-private {@code
 * *ProviderImpl}/{@code *ManagerImpl} in {@code application.service}, each co-located with the
 * package-private final {@code *Mapper} it uses to translate entity ↔ DTO. The router's "service
 * package = *Service only" rule therefore does not apply; impl package-privacy and mapper
 * visibility are enforced below.
 */
class PersistenceGovernanceTest {

  private static final String PKG = "com.krizaka.sovkora.persistence";
  private static JavaClasses persistenceClasses;

  @BeforeAll
  static void importClasses() {
    persistenceClasses = importProductionClasses(PKG);
  }

  @Test
  @DisplayName("[ERR-103] One top-level class per file")
  void oneClassPerFile() {
    assertOneTopLevelClassPerFile(persistenceClasses, PKG);
  }

  @Test
  @DisplayName("[ERR-104] No redundant 'Sovkora' prefix")
  void noRedundantPrefix() {
    assertNoRedundantPrefix(persistenceClasses, PKG);
  }

  @Test
  @DisplayName("[ERR-105] Service *Impl classes are package-private")
  void implsArePackagePrivate() {
    assertImplClassesPackagePrivate(persistenceClasses, PKG + ".application.service");
  }

  // GOV-006: [ERR-107] is not invoked here. This module has no *Mapper class — mapping was inlined
  // into its
  // single consumer, which ERR-129 sanctions — so the rule selected nothing and reported green over
  // the empty set. It runs where mappers exist.

  @Test
  @DisplayName("[ERR-112] No web controllers outside router")
  void noWebControllers() {
    assertNoWebControllers(persistenceClasses, PKG);
  }

  @Test
  @DisplayName("[ADR-009] Instance fields in concrete classes are private")
  void fieldsArePrivate() {
    assertFieldsPrivate(persistenceClasses);
  }

  // GOV-006: [ADR-007] collection-field rule is not invoked here: no non-record, non-entity class
  // in this
  // module declares a List, Map or Set field, so it judged nothing. It runs where such fields
  // exist.

  @Test
  @DisplayName("[GOV-001] No anonymous classes in production")
  void noAnonymousClasses() {
    assertNoAnonymousClasses(persistenceClasses, PKG);
  }

  @Test
  @DisplayName("[GOV-004] No standard streams (use SLF4J)")
  void noStandardStreams() {
    assertNoStandardStreams(persistenceClasses);
  }

  @Test
  @DisplayName("[ERR-109] JPA converters, entities and repositories live in their own sub-packs")
  void persistencePackageHygiene() {
    GovernanceRules.assertPersistencePackageHygiene(persistenceClasses);
  }

  @Test
  @DisplayName("[ERR-113] No Environment injection in production beans")
  void noEnvironmentInjection() {
    SourceFileScanner.assertNoEnvironmentInjection(Path.of("src", "main", "java"));
  }
}
