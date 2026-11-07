package com.krizaka.sovkora.automationservice.infrastructure.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MasterKeySecretSealerTest {

  /** A keyring of two keys, wrapping like the file provider does (AES-GCM, nonce first). */
  private static final class Keyring implements MasterKeyProvider {
    private final Map<String, SecretKey> keys;
    private String active;

    Keyring(String active, Map<String, SecretKey> keys) {
      this.active = active;
      this.keys = keys;
    }

    @Override
    public String activeKeyId() {
      return active;
    }

    @Override
    public WrappedKey wrap(SecretKey dataKey) {
      try {
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keys.get(active), new GCMParameterSpec(128, nonce));
        byte[] sealed = cipher.doFinal(dataKey.getEncoded());
        byte[] out = new byte[nonce.length + sealed.length];
        System.arraycopy(nonce, 0, out, 0, nonce.length);
        System.arraycopy(sealed, 0, out, nonce.length, sealed.length);
        return new WrappedKey(active, out);
      } catch (java.security.GeneralSecurityException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public SecretKey unwrap(String keyId, byte[] wrapped) {
      try {
        SecretKey master = keys.get(keyId);
        if (master == null) {
          throw new com.krizaka.sovkora.assets.domain.port.MasterKeyUnavailableException(
              "unknown key " + keyId, null);
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.DECRYPT_MODE,
            master,
            new GCMParameterSpec(128, java.util.Arrays.copyOfRange(wrapped, 0, 12)));
        return new SecretKeySpec(cipher.doFinal(wrapped, 12, wrapped.length - 12), "AES");
      } catch (java.security.GeneralSecurityException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static SecretKey key(int seed) {
    byte[] bytes = new byte[32];
    java.util.Arrays.fill(bytes, (byte) seed);
    return new SecretKeySpec(bytes, "AES");
  }

  private final Keyring keyring = new Keyring("k1", Map.of("k1", key(1), "k2", key(2)));
  private final MasterKeySecretSealer sealer = new MasterKeySecretSealer(keyring);

  @Test
  @DisplayName("a sealed secret opens for its own scope and is not the plaintext")
  void roundTrip() {
    UUID scope = UUID.randomUUID();
    String sealed = sealer.seal(scope, "whsec_super-secret");
    assertThat(sealed).startsWith("v1.").doesNotContain("super-secret");
    assertThat(sealer.open(scope, sealed)).isEqualTo("whsec_super-secret");
  }

  @Test
  @DisplayName("sealing twice gives two different values (a fresh data key and nonce each time)")
  void freshEveryTime() {
    UUID scope = UUID.randomUUID();
    assertThat(sealer.seal(scope, "x")).isNotEqualTo(sealer.seal(scope, "x"));
  }

  @Test
  @DisplayName("a sealed value cannot be moved to another trigger")
  void boundToItsScope() {
    String sealed = sealer.seal(UUID.randomUUID(), "secret");
    assertThatThrownBy(() -> sealer.open(UUID.randomUUID(), sealed))
        .isInstanceOf(IllegalStateException.class);
  }

  /** A tail that always differs from the real one, whatever its last two characters are. */
  static String forgedTail(String tail) {
    String head = tail.substring(0, tail.length() - 2);
    return head + ("AA".equals(tail.substring(tail.length() - 2)) ? "BB" : "AA");
  }

  @Test
  @DisplayName("the forged tail differs even when the real one ends in AA")
  void forgedTailAlwaysDiffers() {
    assertThat(forgedTail("xyzAA")).isNotEqualTo("xyzAA");
    assertThat(forgedTail("xyzQQ")).isNotEqualTo("xyzQQ");
  }

  @Test
  @DisplayName("a tampered value does not open")
  void tampered() {
    UUID scope = UUID.randomUUID();
    String sealed = sealer.seal(scope, "secret");
    String[] parts = sealed.split("\\.");
    String flipped = forgedTail(parts[4]);
    String forged = String.join(".", parts[0], parts[1], parts[2], parts[3], flipped);
    assertThatThrownBy(() -> sealer.open(scope, forged)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sealer.open(scope, "garbage"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sealer.open(scope, null)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("after a key rotation old values still open and new ones use the new key")
  void keyRotation() {
    UUID scope = UUID.randomUUID();
    String old = sealer.seal(scope, "old-secret");
    keyring.active = "k2";
    String fresh = sealer.seal(scope, "new-secret");
    assertThat(sealer.open(scope, old)).isEqualTo("old-secret");
    assertThat(sealer.open(scope, fresh)).isEqualTo("new-secret");
    assertThat(old.split("\\.")[1]).isNotEqualTo(fresh.split("\\.")[1]);
  }

  @Test
  @DisplayName("a value sealed under a key the deployment no longer holds does not open")
  void retiredKey() {
    UUID scope = UUID.randomUUID();
    String sealed = sealer.seal(scope, "secret");
    MasterKeySecretSealer elsewhere =
        new MasterKeySecretSealer(new Keyring("k9", Map.of("k9", key(9))));
    assertThatThrownBy(() -> elsewhere.open(scope, sealed))
        .isInstanceOf(IllegalStateException.class);
  }
}
