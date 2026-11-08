package com.krizaka.sovkora.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.crypto.infrastructure.adapter.FileMasterKeyProvider;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretSealerTest {

  private SecretSealer sealer(Path dir) throws Exception {
    Path keyring = dir.resolve("master.key");
    FileMasterKeyProvider.addKey(keyring, "k1");
    return new SecretSealer(new FileMasterKeyProvider(keyring));
  }

  @Test
  void sealsAndOpensForTheSameOwner(@TempDir Path dir) throws Exception {
    SecretSealer sealer = sealer(dir);
    UUID owner = UUID.randomUUID();

    String sealed = sealer.seal(owner, "whsec_secret");

    assertThat(sealed).startsWith("v1.").doesNotContain("whsec_secret");
    assertThat(sealer.isSealed(sealed)).isTrue();
    assertThat(sealer.isSealed("whsec_secret")).isFalse();
    assertThat(sealer.open(owner, sealed)).isEqualTo("whsec_secret");
  }

  @Test
  void refusesAnotherOwnerWithoutRevealingTheSecret(@TempDir Path dir) throws Exception {
    SecretSealer sealer = sealer(dir);
    String sealed = sealer.seal(UUID.randomUUID(), "whsec_secret");

    assertThatThrownBy(() -> sealer.open(UUID.randomUUID(), sealed))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("whsec_secret");
  }
}
