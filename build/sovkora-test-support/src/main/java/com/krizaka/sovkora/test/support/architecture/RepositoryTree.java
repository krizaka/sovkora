package com.krizaka.sovkora.test.support.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The files of the repository a rule reads, walked without entering build output or dependencies.
 *
 * <p>The rules that read the whole tree used to walk everything and drop {@code /target/} paths
 * afterwards. In the one reactor (ADR-029) the modules build in parallel ({@code -T 4}): while one
 * module's test walks the tree, another module's {@code target/} changes under it, and the walk
 * fails on a file that was there a moment before (CI run 42419754021, 2026-11-07: {@code
 * NoSuchFileException} on a jar of the knowledge service). Not descending into those folders is
 * what the rules meant, and it is the only way the walk cannot meet a moving file.
 */
public final class RepositoryTree {

  /** Folders no rule reads: build output, dependencies, the repository's own database. */
  static final Set<String> NOT_ENTERED = Set.of("target", "node_modules", ".git");

  private RepositoryTree() {}

  /**
   * Every regular file under {@code root}, in walk order, without entering {@link #NOT_ENTERED}.
   */
  public static List<Path> files(Path root) {
    return walk(root, false);
  }

  /**
   * Every directory under {@code root} (itself included), without entering {@link #NOT_ENTERED}.
   */
  public static List<Path> directories(Path root) {
    return walk(root, true);
  }

  private static List<Path> walk(Path root, boolean directories) {
    List<Path> found = new ArrayList<>();
    if (!Files.isDirectory(root)) {
      return found;
    }
    try {
      Files.walkFileTree(
          root,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
              if (!dir.equals(root) && NOT_ENTERED.contains(dir.getFileName().toString())) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              if (directories) {
                found.add(dir);
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
              if (!directories && attributes.isRegularFile()) {
                found.add(file);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to walk " + root, e);
    }
    return found;
  }
}
