package com.krizaka.sovkora.conversation.architecture;

import static com.krizaka.sovkora.test.support.TestConstants.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.krizaka.sovkora.engine.core.application.interceptor.PromptContextInterceptor;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cross-module architectural boundary enforcement. Guarantees strict unidirectional dependency flow
 * across all Sovkora modules.
 *
 * <p>This test lives in sovkora-router because only the router has compile-time classpath
 * visibility into all sibling modules (core, identity, tools).
 *
 * <p>Enforces the part of the Module Separation Invariant [ERR-102] only this classpath sees:
 * identity ↛ core, identity ↛ tools (identity is fully isolated). The engine's own rules hold the
 * rest in their modules: core ↛ identity ({@code LayerBoundaryTest}), core ↛ tools ({@code
 * CoreIndependenceTest}), tools ↛ identity ({@code ToolsBoundaryTest}), persistence ↛ core and
 * identity ({@code PersistenceBoundaryTest}).
 *
 * @see <a href="../../../../../../AGENTS.md">AGENTS.md §1.A, ERR-102</a>
 */
class CrossModuleBoundaryTest {

  private static JavaClasses allModuleClasses;

  @BeforeAll
  static void importClasses() {
    allModuleClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                "com.krizaka.sovkora.engine.core",
                "com.krizaka.users",
                "com.krizaka.sovkora.engine.tools",
                "com.krizaka.sovkora.engine.persistence",
                "com.krizaka.users.persistence",
                "com.krizaka.sovkora.engine.interceptors",
                "com.krizaka.sovkora.conversation");
  }

  // ── identity isolation ──────────────────────────────────────────────────────

  @Test
  @DisplayName("[ERR-102] Identity must not depend on core layer")
  void identityIsCoreBlind() {
    noClasses()
        .that()
        .resideInAPackage(PKG_IDENTITY)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(PKG_CORE)
        .because(
            "krizaka-users-core is a pure IAM domain hexagon and must never "
                + "import AI orchestration types from sovkora-engine-core [ERR-102]")
        .check(allModuleClasses);
  }

  @Test
  @DisplayName("[ERR-102] Identity must not depend on tools layer")
  void identityIsToolsBlind() {
    noClasses()
        .that()
        .resideInAPackage(PKG_IDENTITY)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(PKG_TOOLS)
        .because("krizaka-users-core must never import tool implementations [ERR-102]")
        .check(allModuleClasses);
  }

  // ── naming governance ─────────────────────────────────────────────────────

  @Test
  @DisplayName("[ERR-104] No redundant 'Sovkora' prefix on internal types")
  void noRedundantProjectPrefix() {
    classes()
        .that()
        .resideInAPackage("com.krizaka.sovkora..")
        .should()
        .haveSimpleNameNotStartingWith("Sovkora")
        .because(
            "The package namespace already establishes ownership. "
                + "Prefixes add semantic noise [ERR-104, Zero-Prefix Invariant].")
        .check(allModuleClasses);
  }

  @Test
  @DisplayName("[GOV-004] Pipeline interceptors must carry role-broadcasting suffixes")
  void interceptorNamingConvention() {
    classes()
        .that()
        .resideInAPackage("com.krizaka.sovkora.engine.interceptors..")
        .and()
        .implement(PromptContextInterceptor.class)
        .should()
        .haveSimpleNameEndingWith("Interceptor")
        .orShould()
        .haveSimpleNameEndingWith("Resolver")
        .orShould()
        .haveSimpleNameEndingWith("Injector")
        .orShould()
        .haveSimpleNameEndingWith("Advisor")
        .orShould()
        .haveSimpleNameEndingWith("Condenser")
        .because(
            "Pipeline processors must explicitly broadcast their pattern role "
                + "via their suffix [GOV-004].")
        .check(allModuleClasses);
  }

  @Test
  @DisplayName(
      "[GOV-004] No classes in any production module should access standard streams (System.out/System.err)")
  void noStandardStreamsInAllModules() {
    com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
        .because("Use SLF4J loggers instead of standard output/error streams")
        .check(allModuleClasses);
  }

  @Test
  @DisplayName(
      "[GOV-003] No hardcoded localhost, credentials, or inline FQCNs in any module's production source")
  void noHardcodedLocalhostOrCredentialsOrEnvInAllModules() throws IOException {
    String[] modules = {
      "sovkora-engine-core",
      "sovkora-conversation-service",
      "krizaka-users-core",
      "sovkora-engine-tools",
      "sovkora-engine-persistence",
      "krizaka-users-persistence"
    };
    List<Path> sourceRoots = new ArrayList<>();
    for (String module : modules) {
      Path path = Path.of(module, "src", "main", "java");
      if (!Files.exists(path)) {
        path = Path.of("..", module, "src", "main", "java");
      }
      if (!Files.exists(path) && "sovkora-conversation-service".equals(module)) {
        path = Path.of("src", "main", "java");
      }
      if (Files.exists(path)) {
        sourceRoots.add(path);
      }
    }

    Pattern fqcnPattern =
        Pattern.compile(
            "(?<![a-zA-Z0-9_@])(java|org|com|jakarta|net)\\.[a-zA-Z0-9_.]+\\.[A-Z][a-zA-Z0-9_]*");

    List<String> violations = new ArrayList<>();
    for (Path sourceRoot : sourceRoots) {
      try (Stream<Path> paths = Files.walk(sourceRoot)) {
        paths
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(
                path -> {
                  try {
                    List<String> lines = Files.readAllLines(path);
                    for (int i = 0; i < lines.size(); i++) {
                      String line = lines.get(i).trim();
                      if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                        continue;
                      }
                      if (line.contains("localhost:") || line.contains("127.0.0.1")) {
                        violations.add(path.getFileName() + ":" + (i + 1) + " -> " + line);
                      }
                      if (line.contains("dummy-key") || line.contains("\"dummy")) {
                        violations.add(
                            path.getFileName()
                                + ":"
                                + (i + 1)
                                + " -> hardcoded credential: "
                                + line);
                      }
                      if (line.contains("System.getenv(")) {
                        violations.add(
                            path.getFileName()
                                + ":"
                                + (i + 1)
                                + " -> raw env access (use typed properties): "
                                + line);
                      }

                      // Strip single line comments
                      int commentIdx = line.indexOf("//");
                      if (commentIdx >= 0) {
                        line = line.substring(0, commentIdx).trim();
                      }
                      if (line.isEmpty()
                          || line.startsWith("import ")
                          || line.startsWith("package ")) {
                        continue;
                      }

                      // Strip string literals
                      String cleanedLine = line.replaceAll("\".*?\"", "\"\"");
                      Matcher matcher = fqcnPattern.matcher(cleanedLine);
                      if (matcher.find()) {
                        violations.add(
                            path.getFileName()
                                + ":"
                                + (i + 1)
                                + " -> Inline FQCN (declare import): "
                                + line);
                      }
                    }
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });
      }
    }

    Assertions.assertTrue(
        violations.isEmpty(),
        "Production source in monorepo contains banned literals or inline FQCNs:\n"
            + String.join("\n", violations));
  }
}
