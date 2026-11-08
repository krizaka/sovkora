package com.krizaka.sovkora.crypto.infrastructure.adapter;

import com.krizaka.sovkora.crypto.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.crypto.domain.port.MasterKeyUnavailableException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The local {@link MasterKeyProvider}: a keyring in a file outside the repository, mode {@code
 * 0600}.
 *
 * <p><b>Not {@code .env}.</b> The master key is the one secret whose compromise is total — every
 * asset ever written, at once — and {@code .env} is the file people paste into chat, copy to a new
 * machine, and mount into containers wholesale. This provider reads a separate file whose only
 * contents are keys, refuses to start if anyone but its owner can read it, and never logs or
 * returns the material.
 *
 * <p><b>Its whole purpose is to be interchangeable with a KMS.</b> The keyring shape is the shape a
 * KMS has: several keys, one of them active, addressed by id. Rotation is one line — {@code active
 * = k2} — and no file in the store is rewritten, because each names the key that wrapped it.
 *
 * <pre>
 * # sovkora master keyring v1
 * active = k2
 * k1 = &lt;base64 of 32 bytes&gt;
 * k2 = &lt;base64 of 32 bytes&gt;
 * </pre>
 */
public final class FileMasterKeyProvider implements MasterKeyProvider {

  private static final Logger logger = LoggerFactory.getLogger(FileMasterKeyProvider.class);

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int NONCE_LENGTH = 12;
  private static final int TAG_BITS = 128;
  private static final int KEY_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private static final Set<PosixFilePermission> FORBIDDEN =
      Set.of(
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.GROUP_WRITE,
          PosixFilePermission.OTHERS_READ,
          PosixFilePermission.OTHERS_WRITE);

  private final Map<String, SecretKey> keyring;
  private final String activeKeyId;

  /**
   * @param keyFile the keyring, outside the repository and readable only by its owner
   * @throws IllegalStateException if it is missing, malformed, or readable by anyone else
   */
  public FileMasterKeyProvider(Path keyFile) {
    Map<String, SecretKey> keys = new LinkedHashMap<>();
    String active = null;
    if (!Files.isReadable(keyFile)) {
      throw new IllegalStateException(
          "master keyring not found at "
              + keyFile
              + ". Assets cannot be written or read without it. Create one with"
              + " `sovkora assets keygen`, keep it outside the repository, and mode it 0600.");
    }
    requireOwnerOnly(keyFile);
    try {
      for (String raw : Files.readAllLines(keyFile)) {
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        int equals = line.indexOf('=');
        if (equals < 0) {
          continue;
        }
        String name = line.substring(0, equals).trim();
        String value = line.substring(equals + 1).trim();
        if ("active".equals(name)) {
          active = value;
        } else {
          byte[] material = Base64.getDecoder().decode(value);
          if (material.length != KEY_BYTES) {
            throw new IllegalStateException(
                "master key '" + name + "' is " + material.length + " bytes; 32 are required");
          }
          keys.put(name, new SecretKeySpec(material, "AES"));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the master keyring at " + keyFile, e);
    } catch (IllegalArgumentException notBase64) {
      throw new IllegalStateException("the master keyring contains a malformed key", notBase64);
    }

    if (keys.isEmpty()) {
      throw new IllegalStateException("the master keyring at " + keyFile + " holds no keys");
    }
    if (active == null || !keys.containsKey(active)) {
      throw new IllegalStateException(
          "the master keyring names no usable active key"
              + " (expected a line `active = <one of the key ids>`)");
    }
    this.keyring = Map.copyOf(keys);
    this.activeKeyId = active;
    logger.info(
        "Asset master keyring loaded: {} key(s), active '{}'. Rotation is a change to that line.",
        keys.size(),
        active);
  }

  /**
   * Refuses a keyring anyone but its owner can read.
   *
   * <p>A world-readable master key is not a smaller version of a secure one, and a warning here
   * would be a warning nobody reads. On a filesystem with no POSIX permissions the check cannot be
   * made and says so rather than pretending it passed.
   */
  private static void requireOwnerOnly(Path keyFile) {
    try {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(keyFile);
      Set<PosixFilePermission> offending = new java.util.LinkedHashSet<>(permissions);
      offending.retainAll(FORBIDDEN);
      if (!offending.isEmpty()) {
        throw new IllegalStateException(
            "the master keyring at "
                + keyFile
                + " is readable beyond its owner ("
                + offending
                + "). Run: chmod 600 "
                + keyFile);
      }
    } catch (UnsupportedOperationException noPosix) {
      logger.warn(
          "Cannot check permissions on the master keyring at {}: this filesystem has no POSIX"
              + " permissions, so its confidentiality is whatever the platform gives it.",
          keyFile,
          noPosix);
    } catch (IOException e) {
      throw new UncheckedIOException("could not stat the master keyring at " + keyFile, e);
    }
  }

  @Override
  public String activeKeyId() {
    return activeKeyId;
  }

  @Override
  public WrappedKey wrap(SecretKey dataKey) {
    byte[] nonce = new byte[NONCE_LENGTH];
    RANDOM.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE, keyring.get(activeKeyId), new GCMParameterSpec(TAG_BITS, nonce));
      // The key id is authenticated with the wrapped key, so a header that renames it fails to
      // unwrap rather than quietly opening under a key it did not claim.
      cipher.updateAAD(activeKeyId.getBytes(StandardCharsets.UTF_8));
      byte[] sealed = cipher.doFinal(dataKey.getEncoded());
      // The nonce lives INSIDE the blob: the envelope format must not know how a provider wraps,
      // or a KMS could not be dropped in beside this one.
      byte[] blob =
          ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
      return new WrappedKey(activeKeyId, blob);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("could not wrap a data key", e);
    }
  }

  @Override
  public SecretKey unwrap(String keyId, byte[] wrapped) {
    SecretKey master = keyring.get(keyId);
    if (master == null) {
      throw new MasterKeyUnavailableException(
          "this deployment holds no master key '"
              + keyId
              + "'; the asset cannot be opened here. A retired key must stay in the keyring for as"
              + " long as one file still names it.",
          null);
    }
    if (wrapped.length <= NONCE_LENGTH) {
      throw new MasterKeyUnavailableException("the wrapped data key is truncated", null);
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, master, new GCMParameterSpec(TAG_BITS, wrapped, 0, NONCE_LENGTH));
      cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
      byte[] material = cipher.doFinal(wrapped, NONCE_LENGTH, wrapped.length - NONCE_LENGTH);
      return new SecretKeySpec(material, "AES");
    } catch (GeneralSecurityException e) {
      throw new MasterKeyUnavailableException(
          "the wrapped data key did not authenticate under master key '" + keyId + "'", e);
    }
  }

  /**
   * Writes a new keyring, or adds a key to an existing one — the whole of rotation.
   *
   * @param keyFile where the keyring lives
   * @param keyId the id of the key to add, which becomes active
   * @throws IOException if the file cannot be written
   */
  public static void addKey(Path keyFile, String keyId) throws IOException {
    byte[] material = new byte[KEY_BYTES];
    RANDOM.nextBytes(material);
    StringBuilder out = new StringBuilder("# sovkora master keyring v1\n");
    out.append("# Keep this file out of the repository and out of .env. Every retired key must\n")
        .append("# stay here for as long as one asset still names it (ADR-054).\n")
        .append("active = ")
        .append(keyId)
        .append('\n');
    if (Files.exists(keyFile)) {
      requireOwnerOnly(keyFile);
      for (String raw : Files.readAllLines(keyFile)) {
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("active")) {
          continue;
        }
        if (line.startsWith(keyId + " ") || line.startsWith(keyId + "=")) {
          throw new IllegalStateException("the keyring already holds a key called '" + keyId + "'");
        }
        out.append(line).append('\n');
      }
    }
    out.append(keyId)
        .append(" = ")
        .append(Base64.getEncoder().encodeToString(material))
        .append('\n');
    Files.createDirectories(keyFile.toAbsolutePath().getParent());
    Files.writeString(keyFile, out.toString());
    try {
      Files.setPosixFilePermissions(
          keyFile, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException noPosix) {
      logger.warn("Could not restrict permissions on {}: no POSIX support here.", keyFile, noPosix);
    }
  }
}
