package com.krizaka.sovkora.assets.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.assets.domain.model.EnvelopeHeader;
import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.assets.domain.port.MasterKeyUnavailableException;
import com.krizaka.sovkora.assets.infrastructure.adapter.FileMasterKeyProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The format, the seek, and the refusals (ADR-054). */
class EncryptedAssetServiceTest {

  private static final int SMALL_BLOCK = 1024;

  private static MasterKeyProvider provider(Path dir, String keyId) throws IOException {
    Path keyFile = dir.resolve("master.key");
    FileMasterKeyProvider.addKey(keyFile, keyId);
    return new FileMasterKeyProvider(keyFile);
  }

  private static byte[] payload(int length) {
    byte[] bytes = new byte[length];
    new Random(42).nextBytes(bytes);
    return bytes;
  }

  @Test
  @DisplayName("what goes in comes out, across every block boundary that matters")
  void roundTripsAtEveryBoundary(@TempDir Path dir) throws Exception {
    EncryptedAssetService service = new EncryptedAssetService(provider(dir, "k1"), SMALL_BLOCK);
    for (int length :
        new int[] {
          0, 1, SMALL_BLOCK - 1, SMALL_BLOCK, SMALL_BLOCK + 1, 5 * SMALL_BLOCK, 5 * SMALL_BLOCK + 7
        }) {
      byte[] plain = payload(length);
      Path file = dir.resolve("asset-" + length);
      service.write(file, plain);
      try (InputStream in = service.read(file)) {
        assertThat(in.readAllBytes()).as("length %d", length).isEqualTo(plain);
      }
      assertThat(service.plainLength(file)).isEqualTo(length);
    }
  }

  @Test
  @DisplayName("the bytes on disk are not the bytes that went in")
  void theFileOnDiskIsCiphertext(@TempDir Path dir) throws Exception {
    EncryptedAssetService service = new EncryptedAssetService(provider(dir, "k1"), SMALL_BLOCK);
    byte[] plain = "carte nationale d'identité 940123456789".getBytes(StandardCharsets.UTF_8);
    Path file = dir.resolve("identity.txt");
    service.write(file, plain);

    byte[] raw = Files.readAllBytes(file);
    assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain("carte nationale");
    assertThat(raw).startsWith(EnvelopeHeader.MAGIC);
    assertThat(raw.length).isGreaterThan(plain.length);
  }

  @Test
  @DisplayName("[the Range trap] a seek decrypts the block it lands in, not the file before it")
  void seeksWithoutDecryptingWhatComesBefore(@TempDir Path dir) throws Exception {
    EncryptedAssetService service = new EncryptedAssetService(provider(dir, "k1"), SMALL_BLOCK);
    byte[] plain = payload(20 * SMALL_BLOCK);
    Path file = dir.resolve("video.mp4");
    service.write(file, plain);

    // Every offset a range could ask for, including block boundaries and the ends.
    for (int offset :
        new int[] {
          0,
          1,
          SMALL_BLOCK - 1,
          SMALL_BLOCK,
          SMALL_BLOCK + 1,
          10 * SMALL_BLOCK + 3,
          20 * SMALL_BLOCK - 1
        }) {
      try (InputStream in = service.read(file, offset)) {
        byte[] tail = in.readAllBytes();
        assertThat(tail)
            .as("from offset %d", offset)
            .isEqualTo(java.util.Arrays.copyOfRange(plain, offset, plain.length));
      }
    }
  }

  @Test
  @DisplayName("skip() seeks too — Spring serves a Range by skipping, not by re-opening")
  void skipSeeks(@TempDir Path dir) throws Exception {
    EncryptedAssetService service = new EncryptedAssetService(provider(dir, "k1"), SMALL_BLOCK);
    byte[] plain = payload(8 * SMALL_BLOCK);
    Path file = dir.resolve("v.mp4");
    service.write(file, plain);

    try (InputStream in = service.read(file)) {
      assertThat(in.skip(3 * SMALL_BLOCK + 17)).isEqualTo(3 * SMALL_BLOCK + 17);
      byte[] window = in.readNBytes(64);
      assertThat(window)
          .isEqualTo(
              java.util.Arrays.copyOfRange(plain, 3 * SMALL_BLOCK + 17, 3 * SMALL_BLOCK + 81));
    }
  }

  @Test
  @DisplayName("a flipped byte anywhere in the payload refuses to open")
  void tamperingIsRefused(@TempDir Path dir) throws Exception {
    EncryptedAssetService service = new EncryptedAssetService(provider(dir, "k1"), SMALL_BLOCK);
    Path file = dir.resolve("a.bin");
    service.write(file, payload(3 * SMALL_BLOCK));

    byte[] raw = Files.readAllBytes(file);
    raw[raw.length - 20] ^= 0x01;
    Files.write(file, raw);

    assertThatThrownBy(
            () -> {
              try (InputStream in = service.read(file)) {
                in.readAllBytes();
              }
            })
        .isInstanceOf(IOException.class)
        .hasMessageContaining("did not authenticate");
  }

  @Test
  @DisplayName("a block moved to another position refuses to open")
  void reorderingIsRefused(@TempDir Path dir) throws Exception {
    EncryptedAssetService service = new EncryptedAssetService(provider(dir, "k1"), SMALL_BLOCK);
    Path file = dir.resolve("b.bin");
    service.write(file, payload(4 * SMALL_BLOCK));

    byte[] raw = Files.readAllBytes(file);
    int header = raw.length - 4 * (SMALL_BLOCK + EnvelopeHeader.TAG_LENGTH);
    int sealed = SMALL_BLOCK + EnvelopeHeader.TAG_LENGTH;
    byte[] first = java.util.Arrays.copyOfRange(raw, header, header + sealed);
    System.arraycopy(raw, header + sealed, raw, header, sealed);
    System.arraycopy(first, 0, raw, header + sealed, sealed);
    Files.write(file, raw);

    assertThatThrownBy(
            () -> {
              try (InputStream in = service.read(file)) {
                in.readAllBytes();
              }
            })
        .isInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("a file wrapped under a retired key still opens; one under an unknown key does not")
  void rotationIsAConfigChangeNotAMigration(@TempDir Path dir) throws Exception {
    Path keyFile = dir.resolve("master.key");
    FileMasterKeyProvider.addKey(keyFile, "k1");
    EncryptedAssetService before =
        new EncryptedAssetService(new FileMasterKeyProvider(keyFile), SMALL_BLOCK);
    byte[] plain = payload(2 * SMALL_BLOCK);
    Path old = dir.resolve("old.bin");
    before.write(old, plain);

    // Rotation: one line in the keyring. Nothing in the store is rewritten.
    FileMasterKeyProvider.addKey(keyFile, "k2");
    MasterKeyProvider rotated = new FileMasterKeyProvider(keyFile);
    EncryptedAssetService after = new EncryptedAssetService(rotated, SMALL_BLOCK);

    assertThat(rotated.activeKeyId()).isEqualTo("k2");
    try (InputStream in = after.read(old)) {
      assertThat(in.readAllBytes()).as("a file written under k1 still opens").isEqualTo(plain);
    }
    Path fresh = dir.resolve("fresh.bin");
    after.write(fresh, plain);
    try (InputStream in = after.read(fresh)) {
      assertThat(in.readAllBytes()).isEqualTo(plain);
    }

    // A keyring that dropped k1 cannot open the old file, and says so rather than degrading.
    Path onlyK2 = dir.resolve("only-k2.key");
    FileMasterKeyProvider.addKey(onlyK2, "k2");
    EncryptedAssetService stranger =
        new EncryptedAssetService(new FileMasterKeyProvider(onlyK2), SMALL_BLOCK);
    assertThatThrownBy(() -> stranger.read(old))
        .isInstanceOf(MasterKeyUnavailableException.class)
        .hasMessageContaining("k1");
  }

  @Test
  @DisplayName("encryptInPlace is idempotent — which is what makes the migration resumable")
  void encryptInPlaceIsIdempotent(@TempDir Path dir) throws Exception {
    EncryptedAssetService service = new EncryptedAssetService(provider(dir, "k1"), SMALL_BLOCK);
    byte[] plain = payload(3 * SMALL_BLOCK + 5);
    Path file = dir.resolve("legacy.png");
    Files.write(file, plain);

    assertThat(service.encryptInPlace(file)).isTrue();
    assertThat(service.encryptInPlace(file)).as("second pass converts nothing").isFalse();
    try (InputStream in = service.read(file)) {
      assertThat(in.readAllBytes()).isEqualTo(plain);
    }
  }
}
