package com.krizaka.sovkora.assets.infrastructure.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.assets.application.service.EncryptedAssetService;
import com.krizaka.sovkora.assets.domain.model.EnvelopeHeader;
import com.krizaka.sovkora.assets.infrastructure.adapter.FileMasterKeyProvider;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The migration is resumable and verifiable, and it says so in numbers (ADR-054 §5). */
class AssetEncryptionToolTest {

  private static Path keyring(Path dir) throws Exception {
    Path keyFile = dir.resolve("master.key");
    FileMasterKeyProvider.addKey(keyFile, "k1");
    return keyFile;
  }

  private static void seedStore(Path root, int count) throws Exception {
    for (int i = 0; i < count; i++) {
      Path file = root.resolve("owner-" + (i % 3)).resolve("job-" + i).resolve("output.bin");
      Files.createDirectories(file.getParent());
      byte[] bytes = new byte[1000 + i];
      new Random(i).nextBytes(bytes);
      Files.write(file, bytes);
    }
  }

  private static List<Path> filesUnder(Path root) throws Exception {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(Files::isRegularFile).sorted().toList();
    }
  }

  @Test
  @DisplayName("it converts what is plaintext and reports the count")
  void convertsAndCounts(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("uploads");
    seedStore(root, 12);

    AssetEncryptionTool.Outcome first = AssetEncryptionTool.migrate(keyring(dir), root);

    assertThat(first.changed()).isEqualTo(12);
    assertThat(first.unchanged()).isZero();
    assertThat(first.failed()).isEmpty();
    assertThat(first.describe()).contains("12 converted");
  }

  @Test
  @DisplayName("re-running converts nothing — 'where did it stop' is a question the store answers")
  void isResumableWithoutALedger(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("uploads");
    seedStore(root, 8);
    Path keyFile = keyring(dir);
    AssetEncryptionTool.migrate(keyFile, root);

    AssetEncryptionTool.Outcome second = AssetEncryptionTool.migrate(keyFile, root);

    assertThat(second.changed()).isZero();
    assertThat(second.unchanged()).isEqualTo(8);
  }

  @Test
  @DisplayName("a half-done store finishes on the next run, and the done half is untouched")
  void finishesAHalfDoneStore(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("uploads");
    seedStore(root, 10);
    Path keyFile = keyring(dir);
    EncryptedAssetService service =
        new EncryptedAssetService(
            new FileMasterKeyProvider(keyFile), EnvelopeHeader.DEFAULT_BLOCK_SIZE);
    int done = 0;
    for (Path file : filesUnder(root)) {
      if (done++ < 4) {
        service.encryptInPlace(file);
      }
    }

    AssetEncryptionTool.Outcome outcome = AssetEncryptionTool.migrate(keyFile, root);

    assertThat(outcome.changed()).isEqualTo(6);
    assertThat(outcome.unchanged()).isEqualTo(4);
    assertThat(AssetEncryptionTool.verify(keyFile, root).changed()).isEqualTo(10);
  }

  @Test
  @DisplayName("verify opens every file — 'it ran' and 'it worked' are different claims")
  void verifyOpensEveryFile(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("uploads");
    seedStore(root, 6);
    Path keyFile = keyring(dir);
    AssetEncryptionTool.migrate(keyFile, root);

    AssetEncryptionTool.Outcome clean = AssetEncryptionTool.verify(keyFile, root);
    assertThat(clean.changed()).isEqualTo(6);
    assertThat(clean.failed()).isEmpty();

    Path victim = filesUnder(root).getFirst();
    byte[] raw = Files.readAllBytes(victim);
    raw[raw.length - 5] ^= 0x01;
    Files.write(victim, raw);

    AssetEncryptionTool.Outcome damaged = AssetEncryptionTool.verify(keyFile, root);
    assertThat(damaged.changed()).isEqualTo(5);
    assertThat(damaged.failed()).hasSize(1).first().asString().contains("output.bin");
  }

  @Test
  @DisplayName("one unreadable file does not strand the rest")
  void oneFailureDoesNotStopTheRun(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("uploads");
    seedStore(root, 5);
    Path keyFile = keyring(dir);
    AssetEncryptionTool.migrate(keyFile, root);

    Path orphanKeyring = dir.resolve("other.key");
    FileMasterKeyProvider.addKey(orphanKeyring, "k9");
    Path orphan = root.resolve("owner-0").resolve("job-orphan").resolve("output.bin");
    Files.createDirectories(orphan.getParent());
    new EncryptedAssetService(
            new FileMasterKeyProvider(orphanKeyring), EnvelopeHeader.DEFAULT_BLOCK_SIZE)
        .write(orphan, "sealed elsewhere".getBytes());

    AssetEncryptionTool.Outcome outcome = AssetEncryptionTool.verify(keyFile, root);

    assertThat(outcome.changed()).isEqualTo(5);
    assertThat(outcome.failed()).hasSize(1).first().asString().contains("k9");
  }

  @Test
  @DisplayName("a converted file still opens to exactly what it held")
  void conversionPreservesTheBytes(@TempDir Path dir) throws Exception {
    Path root = dir.resolve("uploads");
    Path file = root.resolve("o").resolve("j").resolve("scan.pdf");
    Files.createDirectories(file.getParent());
    byte[] original = new byte[200_000];
    new Random(5).nextBytes(original);
    Files.write(file, original);
    Path keyFile = keyring(dir);

    AssetEncryptionTool.migrate(keyFile, root);

    EncryptedAssetService service =
        new EncryptedAssetService(
            new FileMasterKeyProvider(keyFile), EnvelopeHeader.DEFAULT_BLOCK_SIZE);
    try (InputStream in = service.read(file)) {
      assertThat(in.readAllBytes()).isEqualTo(original);
    }
  }
}
