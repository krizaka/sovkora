package com.krizaka.sovkora.tools.infrastructure.sandbox;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sandbox lifecycle manager — manages Jimfs in-memory file systems isolated by jobId.
 *
 * <p>Provides declarative {@link #commit(String)} (flush to real storage) and {@link
 * #rollback(String)} (evict context) hooks. Tracks byte allocation per jobId via {@link AtomicLong}
 * counters and enforces the configured memory cap.
 */
@Component
public class SandboxLifecycle {

  private static final Logger log = LoggerFactory.getLogger(SandboxLifecycle.class);

  private final SandboxProperties properties;
  private final Map<String, SandboxContext> sandboxes = new ConcurrentHashMap<>();

  public SandboxLifecycle(SandboxProperties properties) {
    this.properties = properties;
  }

  /** Get or create a sandbox FileSystem for the given jobId. */
  public Path getSandboxRoot(String jobId) {
    SandboxContext ctx =
        sandboxes.computeIfAbsent(
            jobId,
            id -> {
              FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
              Path root = fs.getPath("/sandbox-" + id);
              try {
                Files.createDirectories(root);
              } catch (IOException e) {
                throw new UncheckedIOException("Failed to create sandbox root for jobId=" + id, e);
              }
              log.debug("Created sandbox for jobId={}", id);
              return new SandboxContext(fs, root, new AtomicLong(0), Instant.now());
            });
    return ctx.root();
  }

  /** Track bytes written to the sandbox. Throws if cap exceeded. */
  public void trackBytes(String jobId, long bytes) {
    SandboxContext ctx = sandboxes.get(jobId);
    if (ctx == null) {
      return;
    }
    long current = ctx.bytesUsed().addAndGet(bytes);
    if (current > properties.maxBytesPerJob()) {
      throw new SandboxCapExceededException(
          "Sandbox for jobId="
              + jobId
              + " exceeded cap: "
              + current
              + " > "
              + properties.maxBytesPerJob());
    }
  }

  /**
   * Commit sandbox contents — flush all bytes from Jimfs to real filesystem.
   *
   * @param jobId the sandbox job identifier
   * @param targetRoot the real filesystem target directory
   */
  public void commit(String jobId, Path targetRoot) {
    SandboxContext ctx = sandboxes.get(jobId);
    if (ctx == null) {
      log.warn("No sandbox found for commit: jobId={}", jobId);
      return;
    }
    try {
      Files.walkFileTree(ctx.root(), new CommitFileVisitor(ctx.root(), targetRoot));
      log.info("Committed sandbox for jobId={}: {} bytes", jobId, ctx.bytesUsed().get());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to commit sandbox for jobId=" + jobId, e);
    } finally {
      evict(jobId);
    }
  }

  /** Rollback sandbox — evict all in-memory context without persisting. */
  public void rollback(String jobId) {
    evict(jobId);
    log.info("Rolled back sandbox for jobId={}", jobId);
  }

  /** Check if a sandbox exists for the given jobId. */
  public boolean exists(String jobId) {
    return sandboxes.containsKey(jobId);
  }

  /** Get the creation time of a sandbox (for eviction scheduling). */
  Instant getCreatedAt(String jobId) {
    SandboxContext ctx = sandboxes.get(jobId);
    return ctx != null ? ctx.createdAt() : null;
  }

  /** Get all active sandbox job IDs (for eviction scheduling). */
  Iterable<String> activeJobIds() {
    return sandboxes.keySet();
  }

  private void evict(String jobId) {
    SandboxContext ctx = sandboxes.remove(jobId);
    if (ctx != null) {
      try {
        ctx.fileSystem().close();
      } catch (IOException e) {
        log.warn("Failed to close Jimfs for jobId={}: {}", jobId, e.getMessage());
      }
    }
  }

  /** Copies each sandbox file to the real target root, preserving relative paths. */
  private static final class CommitFileVisitor extends SimpleFileVisitor<Path> {

    private final Path sandboxRoot;
    private final Path targetRoot;

    CommitFileVisitor(Path sandboxRoot, Path targetRoot) {
      this.sandboxRoot = sandboxRoot;
      this.targetRoot = targetRoot;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      Path relative = sandboxRoot.relativize(file);
      Path target = targetRoot.resolve(relative.toString());
      Files.createDirectories(target.getParent());
      Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
      return FileVisitResult.CONTINUE;
    }
  }

  private record SandboxContext(
      FileSystem fileSystem, Path root, AtomicLong bytesUsed, Instant createdAt) {}
}
