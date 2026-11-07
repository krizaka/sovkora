package com.krizaka.sovkora.crypto.domain.port;

import javax.crypto.SecretKey;

/**
 * Wraps and unwraps data keys. <b>The one interface a KMS and a local key file both satisfy.</b>
 *
 * <p>This port exists so the local path is not a different code path from the one that matters.
 * Everything above it — the writer, the reader, the migrator — knows only that a data key goes in
 * and an opaque blob comes out, and that the blob is addressed by a {@code keyId}. A file-backed
 * provider puts a nonce and a GCM tag inside the blob; a KMS provider puts whatever the KMS
 * returns. Neither is visible here, and no caller may parse a blob.
 *
 * <p>The master key never leaves an implementation. There is no {@code getMasterKey()} and there
 * will not be one: it is the single secret whose compromise is total, and a method returning it
 * would be called.
 */
public interface MasterKeyProvider {

  /**
   * The key new files are wrapped under.
   *
   * <p>Rotation is this value changing. Files already written keep naming the key that wrapped them
   * and keep opening; nothing is rewritten. That is the whole reason for the envelope.
   *
   * @return the active key's id, never blank
   */
  String activeKeyId();

  /**
   * Wraps a freshly generated data key under the active master key.
   *
   * @param dataKey the per-file key, never reused
   * @return the id of the key that wrapped it and the opaque wrapped form
   */
  WrappedKey wrap(SecretKey dataKey);

  /**
   * Unwraps a data key that was wrapped under {@code keyId}.
   *
   * @param keyId the key named in the file's header
   * @param wrapped the opaque blob from that header
   * @return the data key
   * @throws MasterKeyUnavailableException if that key is unknown here, or the blob does not
   *     authenticate under it — an operator must be able to tell "we retired that key" from "this
   *     file has been tampered with", and both are failures to open, never a fallback to plaintext
   */
  SecretKey unwrap(String keyId, byte[] wrapped);

  /**
   * A data key as it is stored: which master key wrapped it, and the result.
   *
   * @param keyId the master key's id, recorded in the file header
   * @param wrapped the opaque wrapped key
   */
  record WrappedKey(String keyId, byte[] wrapped) {

    /** Compact canonical constructor enforcing the port's invariants (ERR-106). */
    public WrappedKey {
      if (keyId == null || keyId.isBlank()) {
        throw new IllegalArgumentException("keyId is required");
      }
      if (wrapped == null || wrapped.length == 0) {
        throw new IllegalArgumentException("wrapped key is required");
      }
      wrapped = wrapped.clone();
    }

    /** Defensive copy — a record's array component is otherwise a shared mutable reference. */
    @Override
    public byte[] wrapped() {
      return wrapped.clone();
    }
  }
}
