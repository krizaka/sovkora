package com.krizaka.sovkora.assets.application.service;

import com.krizaka.sovkora.assets.domain.model.EnvelopeHeader;
import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Reads and writes the envelope format: header bytes, sealed blocks, and nothing else.
 *
 * <p>Deliberately free of files, streams-with-policy and Spring. It is the half of asset encryption
 * that {@code app/envelope.py} has to agree with byte for byte, so it is the half that must be
 * testable from a fixture and readable next to the Python.
 *
 * <p><b>Every block is bound to this exact header.</b> The AAD of block {@code i} is the whole
 * header followed by {@code i}, so truncating the file, editing its recorded length, swapping two
 * blocks, or grafting a block from another file all fail to authenticate rather than decrypt to
 * something.
 */
public final class EnvelopeCodec {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String ALGORITHM = "AES";
  private static final int KEY_BITS = 256;
  private static final SecureRandom RANDOM = new SecureRandom();

  private EnvelopeCodec() {}

  /** A data key nobody else will ever hold: one file, one key. */
  public static SecretKey newDataKey() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance(ALGORITHM);
      generator.init(KEY_BITS, RANDOM);
      return generator.generateKey();
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException("AES key generation is unavailable", impossible);
    }
  }

  /** Eight random bytes; the block index supplies the other four of every nonce. */
  public static byte[] newNoncePrefix() {
    byte[] prefix = new byte[EnvelopeHeader.NONCE_PREFIX_LENGTH];
    RANDOM.nextBytes(prefix);
    return prefix;
  }

  /**
   * Serialises a header. The returned bytes are also the AAD prefix every block is bound to.
   *
   * <pre>
   *   0   8  magic "ORZAENC1"
   *   8   1  version
   *   9   1  algorithm (1 = AES-256-GCM per block)
   *  10   4  blockSize            (big-endian)
   *  14   8  plainLength          (big-endian)
   *  22   2  keyId length         (big-endian)
   *  24   N  keyId, UTF-8
   *  24+N 2  wrapped key length   (big-endian)
   *  26+N M  wrapped key          (opaque)
   *  26+N+M 8 nonce prefix
   * </pre>
   *
   * @param blockSize plaintext bytes per block
   * @param plainLength the plaintext length
   * @param key the wrapped data key and the master key that wrapped it
   * @param noncePrefix this file's nonce prefix
   * @return the header bytes
   */
  public static byte[] writeHeader(
      int blockSize, long plainLength, MasterKeyProvider.WrappedKey key, byte[] noncePrefix) {
    byte[] keyId = key.keyId().getBytes(StandardCharsets.UTF_8);
    byte[] wrapped = key.wrapped();
    ByteBuffer buffer =
        ByteBuffer.allocate(
            EnvelopeHeader.MAGIC.length
                + 1
                + 1
                + 4
                + 8
                + 2
                + keyId.length
                + 2
                + wrapped.length
                + EnvelopeHeader.NONCE_PREFIX_LENGTH);
    buffer.put(EnvelopeHeader.MAGIC);
    buffer.put((byte) EnvelopeHeader.VERSION);
    buffer.put((byte) EnvelopeHeader.ALG_AES_256_GCM);
    buffer.putInt(blockSize);
    buffer.putLong(plainLength);
    buffer.putShort((short) keyId.length);
    buffer.put(keyId);
    buffer.putShort((short) wrapped.length);
    buffer.put(wrapped);
    buffer.put(noncePrefix);
    return buffer.array();
  }

  private static byte[] readExactly(InputStream in, int length) throws IOException {
    byte[] bytes = in.readNBytes(length);
    if (bytes.length < length) {
      throw new IOException("truncated envelope header");
    }
    return bytes;
  }

  /**
   * Parses a header from the front of a stream, leaving it positioned at the first block.
   *
   * @param in a stream positioned at byte zero
   * @return the parsed header
   * @throws IOException if the stream is short, or is not an envelope
   */
  public static EnvelopeHeader readHeader(InputStream in) throws IOException {
    byte[] fixed = in.readNBytes(24);
    if (fixed.length < 24 || !startsWithMagic(fixed)) {
      throw new IOException("not an Sovkora envelope");
    }
    ByteBuffer buffer = ByteBuffer.wrap(fixed);
    buffer.position(EnvelopeHeader.MAGIC.length);
    int version = Byte.toUnsignedInt(buffer.get());
    int algorithm = Byte.toUnsignedInt(buffer.get());
    if (algorithm != EnvelopeHeader.ALG_AES_256_GCM) {
      throw new IOException("unsupported envelope algorithm: " + algorithm);
    }
    int blockSize = buffer.getInt();
    long plainLength = buffer.getLong();
    int keyIdLength = Short.toUnsignedInt(buffer.getShort());

    byte[] keyId = readExactly(in, keyIdLength);
    int wrappedSize = Short.toUnsignedInt(ByteBuffer.wrap(readExactly(in, 2)).getShort());
    byte[] wrapped = readExactly(in, wrappedSize);
    byte[] noncePrefix = readExactly(in, EnvelopeHeader.NONCE_PREFIX_LENGTH);

    int headerLength = 24 + keyIdLength + 2 + wrappedSize + EnvelopeHeader.NONCE_PREFIX_LENGTH;
    return new EnvelopeHeader(
        version,
        blockSize,
        plainLength,
        new String(keyId, StandardCharsets.UTF_8),
        wrapped,
        noncePrefix,
        headerLength);
  }

  /**
   * Whether these opening bytes are an envelope.
   *
   * <p>The migrator's whole resumability rests on this: a file is converted if and only if it
   * starts with the magic, so the state lives in the data instead of in a ledger that can disagree
   * with it.
   *
   * @param opening at least the first eight bytes of a file
   * @return whether it begins with {@link EnvelopeHeader#MAGIC}
   */
  public static boolean startsWithMagic(byte[] opening) {
    return opening != null
        && opening.length >= EnvelopeHeader.MAGIC.length
        && Arrays.equals(Arrays.copyOf(opening, EnvelopeHeader.MAGIC.length), EnvelopeHeader.MAGIC);
  }

  /** Seals one block. {@code aad} is the header bytes; the index is appended by this method. */
  public static byte[] sealBlock(
      SecretKey dataKey, EnvelopeHeader header, int index, byte[] plain, int length) {
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE,
          dataKey,
          new GCMParameterSpec(EnvelopeHeader.TAG_LENGTH * 8, header.nonceForBlock(index)));
      cipher.updateAAD(blockAad(header, index));
      return cipher.doFinal(plain, 0, length);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("could not seal block " + index, e);
    }
  }

  /**
   * Opens one block, or refuses.
   *
   * @throws java.io.IOException if the block does not authenticate — which is a tampered file, a
   *     wrong key or a truncated read, and is never recoverable by returning what it decrypted to
   */
  public static byte[] openBlock(
      SecretKey dataKey, EnvelopeHeader header, int index, byte[] sealed, int length)
      throws IOException {
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          dataKey,
          new GCMParameterSpec(EnvelopeHeader.TAG_LENGTH * 8, header.nonceForBlock(index)));
      cipher.updateAAD(blockAad(header, index));
      return cipher.doFinal(sealed, 0, length);
    } catch (GeneralSecurityException e) {
      throw new IOException("block " + index + " did not authenticate", e);
    }
  }

  /**
   * The additional data block {@code index} is bound to: the whole header, then the index.
   *
   * <p>Binding to the header rather than to a field of it is what makes truncation, a rewritten
   * length, a swapped block and a block grafted from another file all fail at the tag.
   */
  static byte[] blockAad(EnvelopeHeader header, int index) {
    byte[] headerBytes =
        writeHeader(
            header.blockSize(),
            header.plainLength(),
            new MasterKeyProvider.WrappedKey(header.keyId(), header.wrapped()),
            header.noncePrefix());
    return ByteBuffer.allocate(headerBytes.length + 4).put(headerBytes).putInt(index).array();
  }
}
