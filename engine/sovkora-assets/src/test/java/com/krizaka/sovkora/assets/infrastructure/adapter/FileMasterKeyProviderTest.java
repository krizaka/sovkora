package com.krizaka.sovkora.assets.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.assets.application.service.EnvelopeCodec;
import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The one secret whose compromise is total, and the guards around it (ADR-054 §3). */
class FileMasterKeyProviderTest {

  @Test
  @DisplayName("a keyring written by keygen is mode 0600 and nothing else can read it")
  void keygenRestrictsPermissions(@TempDir Path dir) throws Exception {
    Path keyFile = dir.resolve("master.key");
    FileMasterKeyProvider.addKey(keyFile, "k1");

    assertThat(Files.getPosixFilePermissions(keyFile))
        .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  }

  @Test
  @DisplayName("a keyring anyone can read is refused at startup, not warned about")
  void refusesAWorldReadableKeyring(@TempDir Path dir) throws Exception {
    Path keyFile = dir.resolve("master.key");
    FileMasterKeyProvider.addKey(keyFile, "k1");
    Files.setPosixFilePermissions(
        keyFile,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OTHERS_READ));

    assertThatThrownBy(() -> new FileMasterKeyProvider(keyFile))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("readable beyond its owner")
        .hasMessageContaining("chmod 600");
  }

  @Test
  @DisplayName("a missing keyring names what to run, and never falls back to plaintext")
  void refusesAMissingKeyring(@TempDir Path dir) {
    assertThatThrownBy(() -> new FileMasterKeyProvider(dir.resolve("absent.key")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sovkora assets keygen");
  }

  @Test
  @DisplayName("a keyring naming an active key it does not hold is refused")
  void refusesADanglingActiveKey(@TempDir Path dir) throws Exception {
    Path keyFile = dir.resolve("master.key");
    Files.writeString(keyFile, "active = k9\nk1 = " + "A".repeat(43) + "=\n");
    Files.setPosixFilePermissions(
        keyFile, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

    assertThatThrownBy(() -> new FileMasterKeyProvider(keyFile))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no usable active key");
  }

  @Test
  @DisplayName("a wrapped key renamed to another key id refuses to unwrap")
  void theKeyIdIsAuthenticated(@TempDir Path dir) throws Exception {
    Path keyFile = dir.resolve("master.key");
    FileMasterKeyProvider.addKey(keyFile, "k1");
    FileMasterKeyProvider.addKey(keyFile, "k2");
    MasterKeyProvider provider = new FileMasterKeyProvider(keyFile);

    SecretKey dataKey = EnvelopeCodec.newDataKey();
    MasterKeyProvider.WrappedKey wrapped = provider.wrap(dataKey);
    assertThat(wrapped.keyId()).isEqualTo("k2");

    // Claiming k1 wrapped it must fail rather than quietly open under a key it did not name.
    assertThatThrownBy(() -> provider.unwrap("k1", wrapped.wrapped()))
        .hasMessageContaining("did not authenticate");
  }

  @Test
  @DisplayName("the provider exposes no way to read the master key back")
  void thereIsNoGetterForTheMasterKey() {
    // Structural, and the reason it is a test: a method returning the master key would be called.
    assertThat(MasterKeyProvider.class.getMethods())
        .extracting(java.lang.reflect.Method::getName)
        .containsExactlyInAnyOrder("activeKeyId", "wrap", "unwrap");
  }
}
