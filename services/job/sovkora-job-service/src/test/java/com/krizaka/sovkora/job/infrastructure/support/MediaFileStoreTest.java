package com.krizaka.sovkora.job.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.engine.assets.application.service.EncryptedAssetService;
import com.krizaka.sovkora.engine.assets.domain.port.DurableAssetStore;
import com.krizaka.sovkora.engine.assets.infrastructure.config.MediaStorageProperties;
import com.krizaka.sovkora.engine.assets.infrastructure.storage.DirectoryAssetStore;
import com.krizaka.sovkora.engine.assets.infrastructure.storage.NoDurableAssetStore;
import com.krizaka.sovkora.job.infrastructure.adapter.amqp.JobListenerTestAssets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The output of a job outlives the container (the sealed envelope is also in the durable store),
 * and the write primitive creates nothing outside its root (krizaka-internal#4361, Sonar S2083).
 */
class MediaFileStoreTest {

  private static final MediaStorageProperties TENANT =
      new MediaStorageProperties("directory", null, null, "storage.bunnycdn.com", "x", "acme");

  @TempDir Path work;

  private final EncryptedAssetService assets = JobListenerTestAssets.assets();

  @Test
  void aSavedOutputIsAlsoKeptDurably_asTheSealedEnvelope_underTheTenantLayout() throws IOException {
    DurableAssetStore durable = new DirectoryAssetStore(work.resolve("durable"));
    MediaFileStore store = new MediaFileStore(assets, durable, TENANT);

    String url =
        store.save(work.resolve("uploads").toString(), "u1", "j1", pngBytes(), "image.png");

    assertThat(url).isEqualTo("/api/v1/assets/j1/image.png");
    Path kept = work.resolve("durable/t/acme/u/u1/jobs/j1/image.png");
    assertThat(kept).exists();
    assertThat(assets.isEncrypted(kept)).as("only sealed bytes leave the disk").isTrue();
    assertThat(assets.readAllBytes(kept, false)).isEqualTo(pngBytes());
  }

  @Test
  void whenTheDurableCopyFailsTheSaveFailsByName_notReportedStored() {
    DurableAssetStore broken =
        new DurableAssetStore() {
          public boolean enabled() {
            return true;
          }

          public void put(String key, Path file) throws IOException {
            throw new IOException("durable store refused PUT: HTTP 401");
          }

          public boolean fetch(String key, Path target) {
            return false;
          }

          public void delete(String key) {
            // required by interface, not invoked in this test
          }
        };
    MediaMissing.assertEmpty(
        new MediaFileStore(assets, broken, TENANT)
            .save(work.resolve("uploads").toString(), "u1", "j1", pngBytes(), "image.png"));
  }

  @Test
  void afterARedeployTheOutputIsStillReadable_aFreshDiskFetchesTheSealedEnvelopeBack()
      throws IOException {
    DurableAssetStore durable = new DirectoryAssetStore(work.resolve("durable"));
    new MediaFileStore(assets, durable, TENANT)
        .save(work.resolve("uploads-before").toString(), "u1", "j1", pngBytes(), "image.png");

    // A redeploy: the container is new, its disk is empty, the durable store is the same.
    MediaFileStore afterRedeploy = new MediaFileStore(assets, durable, TENANT);
    String freshDisk = work.resolve("uploads-after").toString();

    assertThat(afterRedeploy.read(freshDisk, "u1", "j1", "image.png")).contains(pngBytes());
    assertThat(afterRedeploy.read(freshDisk, "u2", "j1", "image.png"))
        .as("another user's key is another prefix")
        .isEmpty();
  }

  @Test
  void localOnlyWritesNoSecondCopy() throws IOException {
    MediaFileStore store = new MediaFileStore(assets, new NoDurableAssetStore(), TENANT);
    assertThat(store.save(work.resolve("uploads").toString(), "u1", "j1", pngBytes(), "image.png"))
        .isNotEmpty();
    assertThat(Files.exists(work.resolve("durable"))).isFalse();
  }

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

    String url = localStore().save(root.toString(), userId, jobId, pngBytes(), filename);

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

    String url = localStore().save(root.toString(), userId, jobId, pngBytes(), "image.png");

    assertThat(url).isEqualTo("/api/v1/assets/" + jobId + "/image.png");
    assertThat(root.resolve(userId).resolve(jobId).resolve("output/image.png")).isRegularFile();
  }

  private MediaFileStore localStore() {
    return new MediaFileStore(assets, new NoDurableAssetStore(), TENANT);
  }

  private static byte[] pngBytes() {
    return new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
  }

  private static final class MediaMissing {
    static void assertEmpty(String url) {
      assertThat(url).isEmpty();
    }
  }
}
