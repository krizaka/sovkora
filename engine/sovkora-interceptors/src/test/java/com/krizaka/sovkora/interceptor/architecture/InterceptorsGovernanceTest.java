package com.krizaka.sovkora.interceptor.architecture;

import static com.krizaka.sovkora.test.architecture.GovernanceRules.*;

import com.krizaka.sovkora.test.architecture.GovernanceRules;
import com.krizaka.sovkora.test.architecture.PackPurityRules;
import com.krizaka.sovkora.test.architecture.SourceFileScanner;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Governance (naming + structural hygiene) for the sovkora-interceptors module, reusing the shared
 * {@link com.krizaka.sovkora.test.architecture.GovernanceRules}.
 *
 * <p>sovkora-interceptors is a single module organised <b>by concern</b> (context, enrichment,
 * governance, …), not by hexagonal layer. Two router rules therefore do not apply by design:
 *
 * <ul>
 *   <li>The persistence-layout rule [ERR-109]: the governance concern co-locates its policy JPA
 *       entities/repository with the concern, intentionally.
 *   <li>A strict {@code *Interceptor} name rule: every concern's {@code PromptContextInterceptor}
 *       implementations are named {@code *Interceptor}, or by their specialised pipeline role
 *       ({@code *Resolver}/{@code *Injector}/{@code *Condenser}) when several share one concern and
 *       a single {@code *Interceptor} name would collide (e.g. context has both {@code
 *       UserContextInterceptor} and {@code UserContextResolver}).
 * </ul>
 */
class InterceptorsGovernanceTest {

  /**
   * The rules below are repository-wide, not module-scoped: the worst pack coupling lives in the
   * Python media worker, which is in no Maven reactor, so a per-module scan could never see it.
   */
  private static final Path REPOSITORY_ROOT =
      PackPurityRules.locateRepositoryRoot(Path.of(System.getProperty("user.dir")));

  private static final String PKG = "com.krizaka.sovkora.interceptor";
  private static JavaClasses interceptorClasses;

  @BeforeAll
  static void importClasses() {
    interceptorClasses = importProductionClasses(PKG);
  }

  @Test
  @DisplayName("[ERR-103] One top-level class per file")
  void oneClassPerFile() {
    assertOneTopLevelClassPerFile(interceptorClasses, PKG);
  }

  @Test
  @DisplayName("[ERR-104] No redundant 'Sovkora' prefix")
  void noRedundantPrefix() {
    assertNoRedundantPrefix(interceptorClasses, PKG);
  }

  @Test
  @DisplayName("[ERR-107] Mapper classes are final and package-private")
  void mappersAreFinalAndPackagePrivate() {
    assertMappersFinal(interceptorClasses, PKG);
    assertMappersPackagePrivate(interceptorClasses, PKG);
  }

  @Test
  @DisplayName("[ERR-112] No web controllers outside router")
  void noWebControllers() {
    assertNoWebControllers(interceptorClasses, PKG);
  }

  @Test
  @DisplayName("[ADR-009] Instance fields in concrete classes are private")
  void fieldsArePrivate() {
    assertFieldsPrivate(interceptorClasses);
  }

  @Test
  @DisplayName("[ADR-007] Collection fields are private final")
  void collectionFieldsPrivateFinal() {
    assertCollectionFieldsPrivateFinal(interceptorClasses);
  }

  @Test
  @DisplayName("[GOV-001] No anonymous classes in production")
  void noAnonymousClasses() {
    assertNoAnonymousClasses(interceptorClasses, PKG);
  }

  @Test
  @DisplayName("[GOV-004] No standard streams (use SLF4J)")
  void noStandardStreams() {
    assertNoStandardStreams(interceptorClasses);
  }

  @Test
  @DisplayName("[PACK-002] no pack, studio or pack-capability key is a literal in engine code")
  void noPackKeyLiteralsInEngineCode() {
    GovernanceRules.assertNoPackKeyLiterals(REPOSITORY_ROOT);
  }

  @Test
  @DisplayName("[PACK-003] engine code never branches on a pack identifier")
  void noPackKeyConditionalsInEngineCode() {
    GovernanceRules.assertNoPackKeyConditionals(REPOSITORY_ROOT);
  }

  @Test
  @DisplayName("[EXEC-001] every in-process capability's handler_key has an executor")
  void everyCapabilityHasAnExecutor() {
    GovernanceRules.assertEveryCapabilityHasAnExecutor(REPOSITORY_ROOT);
  }

  @Test
  @DisplayName("[EXEC-002] every capability's routing_key is drained by a declared worker")
  void everyCapabilityIsDrained() {
    GovernanceRules.assertEveryCapabilityIsDrained(REPOSITORY_ROOT);
  }

  @Test
  @DisplayName("[ERR-113] No Environment injection in production beans")
  void noEnvironmentInjection() {
    SourceFileScanner.assertNoEnvironmentInjection(Path.of("src", "main", "java"));
  }
}
