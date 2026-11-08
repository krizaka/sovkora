package com.krizaka.sovkora.engine.assets.infrastructure.storage;

import com.krizaka.sovkora.engine.assets.domain.port.DurableAssetStore;
import com.krizaka.sovkora.engine.assets.domain.port.StoredObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the second copy in another directory — a mounted volume, an NFS share, a backup disk. Also
 * the reference adapter of the contract tests.
 */
public final class DirectoryAssetStore implements DurableAssetStore {

  private static final Logger log = LoggerFactory.getLogger(DirectoryAssetStore.class);
  private static final String TEMP_PREFIX = ".orz-";

  private final Path root;

  /**
   * @param root the directory objects are laid out under, by key
   */
  public DirectoryAssetStore(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  @Override
  public boolean enabled() {
    return true;
  }

  @Override
  public void put(String key, Path file) throws IOException {
    Path target = resolve(key);
    Files.createDirectories(target.getParent());
    Path temp = Files.createTempFile(target.getParent(), TEMP_PREFIX, ".tmp");
    try {
      Files.copy(file, temp, StandardCopyOption.REPLACE_EXISTING);
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Override
  public boolean fetch(String key, Path target) throws IOException {
    Path source = resolve(key);
    if (!Files.isRegularFile(source)) {
      return false;
    }
    Path parent = target.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path temp = Files.createTempFile(parent, TEMP_PREFIX, ".tmp");
    try {
      Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
    return true;
  }

  @Override
  public void delete(String key) throws IOException {
    Path target = resolve(key);
    if (!Files.exists(target)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(target)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    } catch (UncheckedIOException e) {
      IOException cause = e.getCause();
      log.debug("Failed to delete file during directory removal", e);
      throw cause;
    }
  }

  @Override
  public List<StoredObject> list(String prefix) throws IOException {
    Path dir = resolve(prefix);
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    List<StoredObject> children = new ArrayList<>();
    try (Stream<Path> entries = Files.list(dir)) {
      for (Path entry : (Iterable<Path>) entries::iterator) {
        if (entry.getFileName().toString().startsWith(TEMP_PREFIX)) {
          continue;
        }
        boolean isDir = Files.isDirectory(entry);
        children.add(
            new StoredObject(
                prefix + entry.getFileName() + (isDir ? "/" : ""),
                Instant.ofEpochMilli(Files.getLastModifiedTime(entry).toMillis())));
      }
    }
    return children;
  }

  private Path resolve(String key) {
    Path resolved = root.resolve(key).normalize();
    if (key.isBlank() || !resolved.startsWith(root) || resolved.equals(root)) {
      throw new IllegalArgumentException("key escapes the store: " + key);
    }
    return resolved;
  }
}
