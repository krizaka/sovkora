package com.krizaka.sovkora.automationservice.infrastructure.adapter.persistence;

import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider.WrappedKey;
import com.krizaka.sovkora.assets.domain.port.MasterKeyUnavailableException;
import com.krizaka.sovkora.automationservice.domain.port.SecretSealer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.stereotype.Component;

/**
 * Seals secrets under the platform's master keyring — the mechanism of the asset store (ADR-054),
 * not a second one: a fresh AES-256 data key per secret, wrapped by the {@link MasterKeyProvider}
 * (a keyring file today, a KMS tomorrow), AES-GCM with the owner's id as additional data, so a
 * sealed value opens only for the trigger it was sealed for. Rotating the keyring needs no rewrite:
 * a value names the key that wrapped it.
 *
 * <p>Stored form: {@code v1.<keyId>.<wrapped data key>.<nonce>.<ciphertext+tag>}, each part
 * base64url.
 */
@Component
class MasterKeySecretSealer implements SecretSealer {

  private static final String VERSION = "v1";
  private static final String CIPHER = "AES/GCM/NoPadding";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int DATA_KEY_BITS = 256;
  private static final int PARTS = 5;

  private final MasterKeyProvider keys;
  private final SecureRandom random = new SecureRandom();

  MasterKeySecretSealer(MasterKeyProvider keys) {
    this.keys = Objects.requireNonNull(keys, "MasterKeyProvider cannot be null");
  }

  @Override
  public String seal(UUID scope, String plaintext) {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(DATA_KEY_BITS, random);
      SecretKey dataKey = generator.generateKey();
      WrappedKey wrapped = keys.wrap(dataKey);
      byte[] nonce = new byte[NONCE_BYTES];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(aad(scope));
      byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return String.join(
          ".",
          VERSION,
          encode(wrapped.keyId().getBytes(StandardCharsets.UTF_8)),
          encode(wrapped.wrapped()),
          encode(nonce),
          encode(sealed));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot seal the secret", e);
    }
  }

  @Override
  public String open(UUID scope, String sealed) {
    String[] parts = sealed == null ? new String[0] : sealed.split("\\.", -1);
    if (parts.length != PARTS || !VERSION.equals(parts[0])) {
      throw new IllegalStateException("not a sealed secret");
    }
    try {
      String keyId = new String(decode(parts[1]), StandardCharsets.UTF_8);
      SecretKey dataKey = keys.unwrap(keyId, decode(parts[2]));
      Cipher cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.DECRYPT_MODE, dataKey, new GCMParameterSpec(TAG_BITS, decode(parts[3])));
      cipher.updateAAD(aad(scope));
      return new String(cipher.doFinal(decode(parts[4])), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException
        | IllegalArgumentException
        | MasterKeyUnavailableException e) {
      // Another trigger, a tampered value or a retired key: one failure, never a fallback.
      throw new IllegalStateException("the sealed secret does not open", e);
    }
  }

  private static byte[] aad(UUID scope) {
    return ByteBuffer.allocate(Long.BYTES * 2)
        .putLong(scope.getMostSignificantBits())
        .putLong(scope.getLeastSignificantBits())
        .array();
  }

  private static String encode(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static byte[] decode(String text) {
    return Base64.getUrlDecoder().decode(text);
  }
}
