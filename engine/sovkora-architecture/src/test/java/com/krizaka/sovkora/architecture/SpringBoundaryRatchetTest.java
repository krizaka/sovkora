package com.krizaka.sovkora.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ADR-092 §4 rule 1, sovkora-engine#112: the domain is pure Java 21. Spring AI lives in {@code
 * sovkora-adapter-spring-ai}; {@code orazaka.core} does not depend on {@code org.springframework..}
 * at all ({@link CoreWithoutSpringTest}, no baseline) and neither does {@code orazaka.business}
 * ({@link BusinessWithoutSpringTest}, no baseline). The classes that break a rule today are the
 * baseline in {@code architecture-ratchet.json}: a new violation fails the build, a fixed one never
 * does.
 *
 * <p>Fixed some? Lock the gain: {@code ./mvnw -pl sovkora-architecture test
 * -Darchitecture.ratchet.update=true} (it never raises a count).
 */
class SpringBoundaryRatchetTest {

  private static final String ADAPTER = "com.krizaka.sovkora.adapter.springai";
  private static final Path BASELINE = Path.of("architecture-ratchet.json");
  private static final ObjectMapper JSON = new ObjectMapper();

  private record Rule(
      String id, String message, Predicate<String> origin, String forbiddenPackagePrefix) {}

  private static final List<Rule> RULES =
      List.of(
          new Rule(
              "spring-ai-outside-adapter",
              "Spring AI is used only in sovkora-adapter-spring-ai (ADR-092 §4 rule 1)",
              pkg -> !pkg.startsWith(ADAPTER),
              "org.springframework.ai"));

  /** The packages of this repository's modules (not the contract jars on the classpath). */
  private static final List<String> OWN_PACKAGES =
      List.of(
          "com.krizaka.sovkora.core",
          "com.krizaka.sovkora.business",
          "com.krizaka.sovkora.interceptor",
          "com.krizaka.sovkora.tools",
          ADAPTER);

  @Test
  void noNewViolation() throws IOException {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.krizaka.sovkora");
    Map<String, Set<String>> current = new TreeMap<>();
    for (Rule rule : RULES) {
      current.put(rule.id(), violators(classes, rule));
    }
    Map<String, Set<String>> baseline = readBaseline();

    if (Boolean.getBoolean("architecture.ratchet.update")) {
      write(Ratchet.lowered(baseline, current));
      return;
    }
    for (Rule rule : RULES) {
      Set<String> known = baseline.getOrDefault(rule.id(), Set.of());
      Set<String> added = Ratchet.increase(known, current.get(rule.id()));
      assertThat(added)
          .as(
              "%s — %d violations now, baseline %d. New: ",
              rule.message(), current.get(rule.id()).size(), known.size())
          .isEmpty();
    }
  }

  private static Set<String> violators(JavaClasses classes, Rule rule) {
    return classes.stream()
        .filter(c -> OWN_PACKAGES.stream().anyMatch(p -> c.getPackageName().startsWith(p)))
        .filter(c -> rule.origin().test(c.getPackageName()))
        .filter(
            c ->
                c.getDirectDependenciesFromSelf().stream()
                    .anyMatch(
                        d ->
                            d.getTargetClass()
                                .getPackageName()
                                .startsWith(rule.forbiddenPackagePrefix())))
        .map(SpringBoundaryRatchetTest::topLevelName)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static String topLevelName(JavaClass c) {
    String name = c.getName();
    int nested = name.indexOf('$');
    return nested < 0 ? name : name.substring(0, nested);
  }

  private static Map<String, Set<String>> readBaseline() throws IOException {
    Map<String, Set<String>> baseline = new TreeMap<>();
    if (!Files.exists(BASELINE)) {
      return baseline;
    }
    JsonNode rules = JSON.readTree(Files.readString(BASELINE)).path("rules");
    for (var entry : rules.properties()) {
      Set<String> classes = new TreeSet<>();
      entry.getValue().path("classes").forEach(n -> classes.add(n.asString()));
      assertThat(entry.getValue().path("count").asInt())
          .as("count of %s must equal its classes", entry.getKey())
          .isEqualTo(classes.size());
      baseline.put(entry.getKey(), classes);
    }
    return baseline;
  }

  private static void write(Map<String, Set<String>> baseline) throws IOException {
    StringBuilder out = new StringBuilder("{\n  \"rules\": {\n");
    int rule = 0;
    for (var entry : new TreeMap<>(baseline).entrySet()) {
      out.append("    \"").append(entry.getKey()).append("\": {\n");
      out.append("      \"count\": ").append(entry.getValue().size()).append(",\n");
      out.append("      \"classes\": [\n");
      int index = 0;
      for (String name : entry.getValue()) {
        out.append("        \"").append(name).append('"');
        out.append(++index < entry.getValue().size() ? ",\n" : "\n");
      }
      out.append("      ]\n    }").append(++rule < baseline.size() ? ",\n" : "\n");
    }
    out.append("  }\n}\n");
    Files.writeString(BASELINE, out.toString());
  }
}
