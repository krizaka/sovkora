package com.krizaka.sovkora.test.support.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The files of the repository a rule reads, walked without entering build output or dependencies,
 * and tolerant of a file that vanishes mid-walk.
 *
 * <p>In the one reactor (ADR-029) the modules build in parallel ({@code -T 4}), so two things
 * change under a walk that reads the whole tree:
 *
 * <ul>
 *   <li>a sibling module's {@code target/} is rebuilt — so the walk never <em>enters</em> {@link
 *       #NOT_ENTERED} (CI run 42419754021, 2026-11-07: a jar of the knowledge service vanished);
 *   <li>a transient file appears and is deleted at a module root, outside those folders — a forked
 *       test JVM's {@code .attach_pid<n>} attach file — so the walk <em>tolerates</em> a file that
 *       is already gone when its attributes are read (CI run 42719357631, 2026-11-09: {@code
 *       NoSuchFileException} on {@code .attach_pid10593} of the automation service).
 * </ul>
 *
 * <p>Pruning alone could not have caught the second: the file was in no folder a rule skips. The
 * two defenses are complementary — prune build output, and skip whatever vanishes anywhere else.
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

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure)
                throws IOException {
              if (vanished(file, failure)) {
                return FileVisitResult.CONTINUE;
              }
              throw failure;
            }
          });
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to walk " + root, e);
    }
    return found;
  }

  /**
   * Whether a walk may skip an entry it could not visit because the entry is gone, not because of a
   * real I/O fault. A parallel reactor build ({@code -T 4}, ADR-029) writes and deletes transient
   * files under a module while a rule walks the tree — a forked test JVM's {@code .attach_pid<n>}
   * attach file, a jar under {@code target/}. Such a file is no part of the committed tree a rule
   * reads, so the walk continues past it; anything still present (a locked or unreadable file) is a
   * genuine fault and is rethrown. Shared with {@code Workspace}, whose walkers run the same risk.
   */
  static boolean vanished(Path file, IOException failure) {
    return failure instanceof NoSuchFileException || Files.notExists(file);
  }
}
