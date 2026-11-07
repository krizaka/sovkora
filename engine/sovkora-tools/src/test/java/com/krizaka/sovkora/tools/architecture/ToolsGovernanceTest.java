package com.krizaka.sovkora.tools.architecture;

import static com.krizaka.sovkora.test.architecture.GovernanceRules.*;

import com.krizaka.sovkora.test.architecture.SourceFileScanner;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Governance (naming + one-package-one-kind) for the sovkora-tools module, reusing the shared
 * {@link com.krizaka.sovkora.test.architecture.GovernanceRules}. Boundary + persistence hygiene
 * live in {@code ToolsBoundaryTest} ([ERR-102/106/109]).
 *
 * <p>The {@code domain.model.*} {@code *Request/*Response} records are the MCP tool I/O contracts
 * (consumed system-wide by the host apps), not HTTP transport DTOs bound by a controller — tools is
 * a library with no web layer. The router's "domain holds no *Request/*Response" rule ([ERR-130])
 * targets transport DTOs and is therefore not applied here.
 */
class ToolsGovernanceTest {

  private static final String PKG = "com.krizaka.sovkora.tools";
  private static JavaClasses toolsClasses;

  @BeforeAll
  static void importClasses() {
    toolsClasses = importProductionClasses(PKG);
  }

  @Test
  @DisplayName("[ERR-103] One top-level class per file")
  void oneClassPerFile() {
    assertOneTopLevelClassPerFile(toolsClasses, PKG);
  }

  @Test
  @DisplayName("[ERR-104] No redundant 'Sovkora' prefix")
  void noRedundantPrefix() {
    assertNoRedundantPrefix(toolsClasses, PKG);
  }

  @Test
  @DisplayName("[ERR-112] No web controllers outside router")
  void noWebControllers() {
    assertNoWebControllers(toolsClasses, PKG);
  }

  @Test
  @DisplayName("[ERR-129] application/service holds only *Service")
  void servicePackageOnlyServices() {
    assertServicePackageOnlyServices(toolsClasses, PKG + ".application.service");
  }

  @Test
  @DisplayName("[HEX-001] Strict hexagonal layering (domain <- application <- infrastructure)")
  void strictHexagonalBoundaries() {
    assertStrictHexagonalBoundaries(toolsClasses, PKG);
  }

  @Test
  @DisplayName("[HEX-002] Domain layer is framework-free (no Spring/JPA/Jackson)")
  void domainPurity() {
    assertDomainPurity(toolsClasses, PKG);
  }

  // GOV-006: [ERR-130] adapter-package-kind is not invoked here: no class sits directly in
  // infrastructure.adapter.persistence — tools keeps entity/ and repository/ below it, which that
  // rule
  // does not scope — so it examined nothing.

  @Test
  @DisplayName("[ADR-009] Instance fields in concrete classes are private")
  void fieldsArePrivate() {
    assertFieldsPrivate(toolsClasses);
  }

  @Test
  @DisplayName("[ADR-007] Collection fields are private final")
  void collectionFieldsPrivateFinal() {
    assertCollectionFieldsPrivateFinal(toolsClasses);
  }

  @Test
  @DisplayName("[GOV-001] No anonymous classes in production")
  void noAnonymousClasses() {
    assertNoAnonymousClasses(toolsClasses, PKG);
  }

  @Test
  @DisplayName("[GOV-004] No standard streams (use SLF4J)")
  void noStandardStreams() {
    assertNoStandardStreams(toolsClasses);
  }

  @Test
  @DisplayName("[ERR-113] No Environment injection in production beans")
  void noEnvironmentInjection() {
    SourceFileScanner.assertNoEnvironmentInjection(Path.of("src", "main", "java"));
  }
}
