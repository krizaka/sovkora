package com.krizaka.sovkora.assets.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.assets.infrastructure.adapter.FileMasterKeyProvider;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Writes the fixture the Python side reads, and reads the one it writes.
 *
 * <p>The store is written by three processes in two languages, and the way that breaks is silently:
 * one side ships a change to the header and the other keeps opening the files it wrote itself. This
 * test produces {@code interop/java-written.bin} under {@code target/}, and {@code
 * test_main.py::TestEnvelopeInterop} opens it with a fixed key and compares plaintext. Neither side
 * can drift without the other going red.
 */
class CrossLanguageFixtureTest {

  /** A fixed key, in the test only: a fixture both languages can open must not be random. */
  private static final byte[] FIXED_KEY = new byte[32];

  static {
    new Random(1234).nextBytes(FIXED_KEY);
  }

  @Test
  @DisplayName("the Java writer emits the fixture the Python reader is asserted against")
  void writesTheInteropFixture(@TempDir Path scratch) throws Exception {
    Path out = Path.of("target", "interop");
    Files.createDirectories(out);

    Path keyFile = out.resolve("fixture.key");
    Files.writeString(
        keyFile,
        "# fixture keyring — test material only\nactive = fixture\nfixture = "
            + Base64.getEncoder().encodeToString(FIXED_KEY)
            + "\n");
    Files.setPosixFilePermissions(
        keyFile,
        java.util.Set.of(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));

    byte[] payload = new byte[3 * 4096 + 271];
    new Random(99).nextBytes(payload);
    Files.write(out.resolve("plain.bin"), payload);

    EncryptedAssetService service =
        new EncryptedAssetService(new FileMasterKeyProvider(keyFile), 4096);
    Path sealed = out.resolve("java-written.bin");
    service.write(sealed, payload);

    // And the Java reader opens what the Java writer produced, so a red Python side is the
    // Python side and not a broken fixture.
    try (InputStream in = service.read(sealed)) {
      assertThat(in.readAllBytes()).isEqualTo(payload);
    }
    assertThat(Files.exists(sealed)).isTrue();
  }

  @Test
  @DisplayName("the Java reader opens what the Python writer produced, when Python has run")
  void readsThePythonFixture() throws Exception {
    Path sealed = Path.of("target", "interop", "python-written.bin");
    Path plain = Path.of("target", "interop", "plain.bin");
    Path keyFile = Path.of("target", "interop", "fixture.key");
    if (!Files.exists(sealed) || !Files.exists(keyFile)) {
      // The Python suite writes it. Absent, this asserts nothing rather than failing a Java-only
      // build — and `sovkora test` runs both, which is where the pair is enforced.
      return;
    }
    EncryptedAssetService service =
        new EncryptedAssetService(new FileMasterKeyProvider(keyFile), 4096);
    try (InputStream in = service.read(sealed)) {
      assertThat(in.readAllBytes()).isEqualTo(Files.readAllBytes(plain));
    }
  }
}
