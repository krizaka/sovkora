package com.krizaka.sovkora.assets.domain.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The header of an encrypted asset: everything a reader needs before it can ask for a key.
 *
 * <p><b>Envelope, not direct encryption.</b> Each file carries its own data key, wrapped under a
 * master key this format never sees. Rotating the master key therefore rewraps a key per file — or,
 * done properly, rewraps nothing at all: {@link #keyId} says which master key wrapped this one, so
 * a new active key applies to new files while every old file still opens. Encrypting the bytes
 * directly under the master key would make rotation a rewrite of every file in the store, which is
 * the first thing a compliance audit asks about and the last thing anyone wants to schedule.
 *
 * <p><b>{@link #wrapped} is opaque on purpose.</b> The format stores whatever the key provider
 * returned and never parses it. A local provider puts a nonce and a GCM tag in there; a KMS puts
 * its own ciphertext blob. Neither the writer nor the reader knows which, which is what lets the
 * local path be the same code path as the one that matters.
 *
 * <p><b>Block-addressable.</b> AES-GCM cannot be decrypted from an arbitrary offset — the tag
 * covers the whole message — so a single-message file would make an HTTP {@code Range} on a video
 * decrypt the entire file to serve one second of it. The payload is therefore a sequence of
 * independently sealed blocks of {@link #blockSize} plaintext bytes each, and a range decrypts
 * {@code ceil(length / blockSize)} of them. See {@code docs/ASSET_ENCRYPTION.md} for the layout
 * that {@code app/envelope.py} implements on the other side.
 *
 * @param version the format version; a reader refuses anything it does not know
 * @param blockSize plaintext bytes per sealed block
 * @param plainLength the plaintext length, which is also the {@code Content-Length} served
 * @param keyId which master key wrapped {@link #wrapped}
 * @param wrapped the wrapped data key, opaque to this format
 * @param noncePrefix eight random bytes per file; the block index supplies the other four
 * @param headerLength where the first block starts
 */
public record EnvelopeHeader(
    int version,
    int blockSize,
    long plainLength,
    String keyId,
    byte[] wrapped,
    byte[] noncePrefix,
    int headerLength) {

  /** Eight bytes a reader can test for without decrypting anything. */
  public static final byte[] MAGIC = "ORZAENC1".getBytes(StandardCharsets.US_ASCII);

  /** The only version this build writes. */
  public static final int VERSION = 1;

  /** AES-256-GCM, one sealed message per block. */
  public static final int ALG_AES_256_GCM = 1;

  /** 64 KiB: a {@code Range} over-reads at most this much at each end. */
  public static final int DEFAULT_BLOCK_SIZE = 64 * 1024;

  /** The bytes a block's nonce takes from the file, before the block index. */
  public static final int NONCE_PREFIX_LENGTH = 8;

  /** GCM tag length in bytes, appended to every block. */
  public static final int TAG_LENGTH = 16;

  /** Compact canonical constructor enforcing the format's invariants (ERR-106). */
  public EnvelopeHeader {
    if (version != VERSION) {
      throw new IllegalArgumentException("unsupported envelope version: " + version);
    }
    if (blockSize <= 0) {
      throw new IllegalArgumentException("blockSize must be positive, was " + blockSize);
    }
    if (plainLength < 0) {
      throw new IllegalArgumentException("plainLength must not be negative");
    }
    if (keyId == null || keyId.isBlank()) {
      throw new IllegalArgumentException(
          "keyId is required: a file that cannot name its master key cannot be opened");
    }
    if (wrapped == null || wrapped.length == 0) {
      throw new IllegalArgumentException("wrapped data key is required");
    }
    if (noncePrefix == null || noncePrefix.length != NONCE_PREFIX_LENGTH) {
      throw new IllegalArgumentException("noncePrefix must be " + NONCE_PREFIX_LENGTH + " bytes");
    }
    wrapped = wrapped.clone();
    noncePrefix = noncePrefix.clone();
  }

  /** How many sealed blocks the payload holds. Zero-length plaintext holds none. */
  public int blockCount() {
    return (int) ((plainLength + blockSize - 1) / blockSize);
  }

  /** Bytes on disk for a whole block: its plaintext plus its tag. */
  public int sealedBlockSize() {
    return blockSize + TAG_LENGTH;
  }

  /** Where block {@code index} starts on disk. */
  public long offsetOfBlock(int index) {
    return (long) headerLength + (long) index * sealedBlockSize();
  }

  /** Plaintext bytes in block {@code index} — the last one is usually short. */
  public int plainBytesInBlock(int index) {
    long remaining = plainLength - (long) index * blockSize;
    return (int) Math.min(blockSize, Math.max(0, remaining));
  }

  /**
   * The nonce for a block: the file's prefix and the block's index.
   *
   * <p>Unique by construction. The data key is unique per file, so the counter only has to be
   * unique within one file, and the random prefix means two files never share a nonce space even if
   * a key were somehow reused.
   */
  public byte[] nonceForBlock(int index) {
    return ByteBuffer.allocate(12).put(noncePrefix).putInt(index).array();
  }

  /** Defensive copy — a record's array component is otherwise a shared mutable reference. */
  @Override
  public byte[] wrapped() {
    return wrapped.clone();
  }

  /** Defensive copy — see {@link #wrapped()}. */
  @Override
  public byte[] noncePrefix() {
    return noncePrefix.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof EnvelopeHeader that
        && version == that.version
        && blockSize == that.blockSize
        && plainLength == that.plainLength
        && headerLength == that.headerLength
        && keyId.equals(that.keyId)
        && Arrays.equals(wrapped, that.wrapped)
        && Arrays.equals(noncePrefix, that.noncePrefix);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        version,
        blockSize,
        plainLength,
        headerLength,
        keyId,
        Arrays.hashCode(wrapped),
        Arrays.hashCode(noncePrefix));
  }

  /** Never the key material, wrapped or otherwise. */
  @Override
  public String toString() {
    return "EnvelopeHeader[v="
        + version
        + ", blockSize="
        + blockSize
        + ", plainLength="
        + plainLength
        + ", keyId="
        + keyId
        + ", wrapped=<"
        + wrapped.length
        + " bytes>, headerLength="
        + headerLength
        + "]";
  }
}
