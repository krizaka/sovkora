package com.krizaka.sovkora.test.support.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole-tree rules never walk into build output (CI run 42419754021: a jar vanished mid-walk).
 */
class RepositoryTreeTest {

  @TempDir Path root;

  /**
   * A folder the walk cannot enter stands for one that changes under it: a parallel module's {@code
   * target/} being rebuilt.
   */
  private Path locked;

  @AfterEach
  void unlock() throws IOException {
    if (locked != null) {
      Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
    }
  }

  @Test
  @DisplayName(
      "the files and folders of the tree are found, and target, node_modules and .git are never entered")
  void skipsBuildOutputAndDependencies() throws IOException {
    write("service/src/main/java/A.java");
    write("service/target/classes/B.java");
    write("app/node_modules/x/C.java");
    write(".git/objects/D");

    assertThat(RepositoryTree.files(root))
        .containsExactly(root.resolve("service/src/main/java/A.java"));
    assertThat(RepositoryTree.directories(root))
        .contains(root, root.resolve("service/src/main/java"))
        .noneMatch(d -> d.toString().contains("target") || d.toString().contains("node_modules"));
  }

  @Test
  @DisplayName("a missing root is an empty tree, not a failure")
  void missingRoot() {
    assertThat(RepositoryTree.files(root.resolve("absent"))).isEmpty();
  }

  @Test
  @DisplayName(
      "a file gone when the walk reaches it is skipped; a present but unreadable one still fails")
  void toleratesAFileThatVanishesMidWalk() throws IOException {
    Path gone = root.resolve("services/automation/.attach_pid10593");
    Path present = root.resolve("services/automation/pom.xml");
    Files.createDirectories(present.getParent());
    Files.writeString(present, "");

    // A forked test JVM's attach file, listed a moment ago and deleted before its attributes could
    // be read: the walk continues past it (CI run 42719357631, 2026-11-09). Reported either as
    // NoSuchFileException, or as a plain IOException over a path that is already gone.
    assertThat(RepositoryTree.vanished(gone, new NoSuchFileException(gone.toString()))).isTrue();
    assertThat(RepositoryTree.vanished(gone, new IOException("gone"))).isTrue();

    // A file still on disk that genuinely could not be read is a real fault, not a race: rethrow
    // it.
    assertThat(RepositoryTree.vanished(present, new AccessDeniedException(present.toString())))
        .isFalse();
  }

  @Test
  @DisplayName("a whole-tree rule passes while a module's target folder cannot be read")
  void aRuleIgnoresAMovingTarget() throws IOException {
    write(
        "service/src/main/resources/db/migration/V1__baseline.sql",
        "CREATE TABLE job (id uuid PRIMARY KEY);");
    locked = Files.createDirectories(root.resolve("service/target/locked"));
    Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));

    assertThatCode(() -> SqlBoundaryRules.assertNoCrossContextForeignKeysInMigrations(root))
        .doesNotThrowAnyException();
  }

  private void write(String path) throws IOException {
    write(path, "");
  }

  private void write(String path, String content) throws IOException {
    Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
