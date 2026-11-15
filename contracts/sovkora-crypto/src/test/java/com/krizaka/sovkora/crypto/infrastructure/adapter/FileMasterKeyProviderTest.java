package com.krizaka.sovkora.crypto.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileMasterKeyProviderTest {

  private static final String KEY_32 = Base64.getEncoder().encodeToString(new byte[32]);

  private static Path keyring(Path dir, String content) throws Exception {
    Path file = dir.resolve("master.key");
    Files.writeString(file, content);
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    return file;
  }

  @Test
  void skipsBlankCommentAndEqualsFreeLinesAndReadsTheActiveKey(@TempDir Path dir) throws Exception {
    Path file =
        keyring(dir, "# a comment\n\nnoise without equals\nk1 = " + KEY_32 + "\nactive = k1\n");

    assertThat(new FileMasterKeyProvider(file).activeKeyId()).isEqualTo("k1");
  }

  @Test
  void refusesAKeyThatIsNotBase64(@TempDir Path dir) throws Exception {
    Path file = keyring(dir, "k1 = !!!not-base64!!!\nactive = k1\n");

    assertThatThrownBy(() -> new FileMasterKeyProvider(file))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("the master keyring contains a malformed key");
  }

  @Test
  void refusesAKeyOfTheWrongLength(@TempDir Path dir) throws Exception {
    Path file = keyring(dir, "k1 = " + Base64.getEncoder().encodeToString(new byte[16]) + "\n");

    assertThatThrownBy(() -> new FileMasterKeyProvider(file))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("master key 'k1' is 16 bytes; 32 are required");
  }

  @Test
  void refusesAKeyringWithNoKeysOrNoUsableActiveKey(@TempDir Path dir) throws Exception {
    Path empty = keyring(dir, "active = k1\n");
    assertThatThrownBy(() -> new FileMasterKeyProvider(empty))
        .hasMessageContaining("holds no keys");

    Path unnamed = keyring(dir, "k1 = " + KEY_32 + "\nactive = k2\n");
    assertThatThrownBy(() -> new FileMasterKeyProvider(unnamed))
        .hasMessageContaining("names no usable active key");
  }
}
