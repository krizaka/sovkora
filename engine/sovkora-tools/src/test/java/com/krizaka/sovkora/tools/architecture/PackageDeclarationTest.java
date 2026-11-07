package com.krizaka.sovkora.tools.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every compilation unit of every module declares the package its folder spells (#37). A file whose
 * {@code package} line disagrees with its path breaks navigation, module exports and the layer
 * rules.
 */
class PackageDeclarationTest {

  private static final Pattern PACKAGE =
      Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

  @Test
  @DisplayName("[#37] the package statement of every source file matches its folder")
  void packageMatchesFolder() throws IOException {
    List<String> mismatches = new ArrayList<>();
    Path workspace = Path.of("..").toAbsolutePath().normalize();
    try (Stream<Path> modules = Files.list(workspace)) {
      for (Path module :
          modules.filter(p -> p.getFileName().toString().startsWith("sovkora-")).toList()) {
        for (String source : List.of("main", "test")) {
          Path root = module.resolve("src").resolve(source).resolve("java");
          if (Files.isDirectory(root)) {
            collect(root, mismatches);
          }
        }
      }
    }
    assertThat(mismatches)
        .as("files whose package statement disagrees with their folder")
        .isEmpty();
  }

  private static void collect(Path root, List<String> mismatches) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        if ("module-info.java".equals(file.getFileName().toString())) {
          continue;
        }
        Matcher declared = PACKAGE.matcher(Files.readString(file));
        String expected = root.relativize(file.getParent()).toString().replace('/', '.');
        if (declared.find() && !declared.group(1).equals(expected)) {
          mismatches.add(
              root.getParent().getParent().getParent().getFileName()
                  + ": "
                  + root.relativize(file));
        }
      }
    }
  }
}
