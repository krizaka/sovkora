package com.krizaka.sovkora.job.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.engine.assets.infrastructure.config.MediaStorageProperties;
import com.krizaka.sovkora.engine.assets.infrastructure.storage.NoDurableAssetStore;
import com.krizaka.sovkora.job.infrastructure.adapter.amqp.JobListenerTestAssets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The write primitive creates nothing outside its root (krizaka-internal#4361, Sonar S2083): an id
 * that is not one plain segment is refused before a single directory is made.
 */
class MediaFileStoreTest {

  private static final MediaStorageProperties TENANT =
      new MediaStorageProperties("local", null, null, "storage.bunnycdn.com", null, "default");

  @TempDir Path work;

  private final MediaFileStore store =
      new MediaFileStore(JobListenerTestAssets.assets(), new NoDurableAssetStore(), TENANT);

  /**
   * The first row is the one that escaped before the fix: the file lands back inside the root, so
   * its containment check passed, while the directories of a {@code ..} owner were made outside.
   */
  @ParameterizedTest(name = "user {0}, job {1}, file {2}")
  @CsvSource({
    "../escaped, j1, ../../../uploads/image.png",
    "u1, ../escaped, ../../../u1/image.png",
    "../../escaped, j1, image.png",
    "u1, ../../../escaped, image.png",
    "'..', j1, image.png",
    "u1, '..', image.png",
    "u1/../.., j1, image.png",
    "/tmp/escaped, j1, image.png",
    "u1, j1, ../image.png",
    "'u1\nforged', j1, image.png"
  })
  void anIdThatIsNotOnePlainSegmentCreatesNothingAnywhere(
      String userId, String jobId, String filename) throws IOException {
    Path root = Files.createDirectories(work.resolve("a/b/c/uploads"));

    String url = store.save(root.toString(), userId, jobId, new byte[] {1, 2, 3}, filename);

    assertThat(url).isEmpty();
    try (Stream<Path> everything = Files.walk(work)) {
      assertThat(everything.filter(p -> !p.equals(work)))
          .as("no directory or file made anywhere under the work tree")
          .containsExactlyInAnyOrder(
              work.resolve("a"), work.resolve("a/b"), work.resolve("a/b/c"), root);
    }
  }

  @ParameterizedTest(name = "user {0}, job {1}")
  @CsvSource({"u1, j1", "user-1, 3f2b6c1e-9d4a-4c1b-8f0e-2a7d5b9c0e11", "actor_7, job.1"})
  void aPlainIdIsWrittenUnderItsOwnDirectory(String userId, String jobId) {
    Path root = work.resolve("uploads");

    String url = store.save(root.toString(), userId, jobId, new byte[] {1, 2, 3}, "image.png");

    assertThat(url).isEqualTo("/api/v1/assets/" + jobId + "/image.png");
    assertThat(root.resolve(userId).resolve(jobId).resolve("output/image.png")).isRegularFile();
  }
}
