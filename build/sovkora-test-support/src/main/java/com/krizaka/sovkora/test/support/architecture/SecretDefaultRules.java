package com.krizaka.sovkora.test.support.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * [CFG-002] A {@code *_SECRET} variable has no default in a module's application configuration.
 *
 * <p>{@code ${IDENTITY_JWT_SECRET:<a value>}} stood in six services' {@code application.yml}: a
 * service started without the variable verified sessions — and {@code SERVICE} tokens — with a
 * value anyone could read in the repository, and nothing said so. A secret is set by the
 * environment or the service does not start; a local run reads it from the developer's {@code
 * .env}, never from a committed file. An empty default ({@code ${X_SECRET:}}) is allowed: it binds
 * a blank value, which the owning properties refuse at startup with a message naming the variable.
 */
public final class SecretDefaultRules {

  /** The innermost placeholder of a {@code *_SECRET} variable with a non-empty literal default. */
  private static final Pattern DEFAULTED = Pattern.compile("\\$\\{([A-Z0-9_]*_SECRET):([^}$]+)}");

  private SecretDefaultRules() {}

  /**
   * [CFG-002] over the application configuration of every module of the repository: each {@code
   * src/main/resources} folder that holds an {@code application*} file. Skipped outside the Sovkora
   * repository; the folders under a dot directory (the krizaka-java checkout) are not ours.
   *
   * @param root the repository root ({@link Workspace#rootOrRepository})
   */
  public static void assertNoModuleDefaultsASecret(Path root) {
    Workspace.require(root, "CFG-002");
    List<Path> folders =
        RepositoryTree.directories(root).stream()
            .filter(d -> d.endsWith(Path.of("src", "main", "resources")))
            .filter(d -> !root.relativize(d).toString().startsWith("."))
            .filter(SecretDefaultRules::holdsApplicationConfig)
            .sorted()
            .toList();
    for (Path folder :
        GovernanceSubjects.require("CFG-002", "module configuration folders", folders)) {
      assertNoSecretHasADefault(folder);
    }
  }

  /**
   * [CFG-002] over the application configuration files of one folder.
   *
   * @param resources the folder that holds {@code application*.yml} / {@code .properties}
   */
  public static void assertNoSecretHasADefault(Path resources) {
    List<Path> configs;
    try (Stream<Path> files = Files.list(resources)) {
      configs = files.filter(SecretDefaultRules::isApplicationConfig).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot list " + resources, e);
    }
    List<String> found = new ArrayList<>();
    for (Path config :
        GovernanceSubjects.require("CFG-002", "application configuration files", configs)) {
      defaultedSecrets(read(config)).forEach(v -> found.add(config.getFileName() + ": " + v));
    }
    if (!found.isEmpty()) {
      throw new AssertionError(
          "[CFG-002] a secret is set by the environment, never by a default in the file: " + found);
    }
  }

  /**
   * The {@code *_SECRET} variables given a literal default in one configuration text.
   *
   * @param text the content of an application configuration file
   * @return the variable names, in order of appearance
   */
  static List<String> defaultedSecrets(String text) {
    List<String> names = new ArrayList<>();
    Matcher m = DEFAULTED.matcher(text);
    while (m.find()) {
      names.add(m.group(1));
    }
    return names;
  }

  private static boolean holdsApplicationConfig(Path folder) {
    try (Stream<Path> files = Files.list(folder)) {
      return files.anyMatch(SecretDefaultRules::isApplicationConfig);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot list " + folder, e);
    }
  }

  private static boolean isApplicationConfig(Path file) {
    String name = file.getFileName().toString();
    return name.startsWith("application")
        && (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties"));
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + file, e);
    }
  }
}
