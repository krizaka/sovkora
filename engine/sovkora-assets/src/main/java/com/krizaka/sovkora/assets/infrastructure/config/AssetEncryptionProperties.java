package com.krizaka.sovkora.assets.infrastructure.config;

import com.krizaka.sovkora.assets.domain.model.EnvelopeHeader;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How this deployment encrypts its asset store ({@code orazaka.assets.encryption}).
 *
 * <p>Infrastructure wiring only: where the keyring is and how wide a block is. What is IN the
 * store, and which packs may use it, is decided elsewhere (AGENTS.md §4).
 *
 * @param enabled whether new writes are encrypted. Off is a migration state and a loud one
 * @param masterKeyFile the keyring, outside the repository and outside {@code .env}. {@code ~} is
 *     expanded, because the whole point is that it is not next to the code
 * @param blockSize plaintext bytes per sealed block; a {@code Range} over-reads at most this much
 *     at each end
 * @param acceptPlaintext whether a file that is not an envelope is still served. <b>True only while
 *     a migration is running.</b> A permanent tolerance is a permanent door: with it on, an
 *     attacker who can write to the store can also make the store readable
 */
@ConfigurationProperties(prefix = "orazaka.assets.encryption")
public record AssetEncryptionProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("${user.home}/.sovkora/master.key") String masterKeyFile,
    @DefaultValue("65536") int blockSize,
    @DefaultValue("false") boolean acceptPlaintext) {

  /** Compact canonical constructor enforcing the wiring's invariants (ERR-106). */
  public AssetEncryptionProperties {
    if (masterKeyFile == null || masterKeyFile.isBlank()) {
      throw new IllegalArgumentException("orazaka.assets.encryption.master-key-file is required");
    }
    if (blockSize < 1024 || blockSize % 16 != 0) {
      throw new IllegalArgumentException(
          "blockSize must be at least 1024 and a multiple of 16, was " + blockSize);
    }
    if (blockSize > 8 * EnvelopeHeader.DEFAULT_BLOCK_SIZE * 16) {
      // A block is the granularity of a Range read; a huge one gives back what blocks bought.
      throw new IllegalArgumentException(
          "blockSize is too large to serve a Range from: " + blockSize);
    }
  }

  /** The keyring path with {@code ~} expanded to this user's home. */
  public java.nio.file.Path resolvedMasterKeyFile() {
    String path =
        masterKeyFile.startsWith("~/")
            ? System.getProperty("user.home") + masterKeyFile.substring(1)
            : masterKeyFile;
    return java.nio.file.Path.of(path);
  }
}
