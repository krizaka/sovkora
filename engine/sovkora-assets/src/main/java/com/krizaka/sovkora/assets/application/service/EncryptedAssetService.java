package com.krizaka.sovkora.assets.application.service;

import com.krizaka.sovkora.assets.domain.model.EnvelopeHeader;
import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes and reads encrypted assets on a filesystem.
 *
 * <p>The one place that knows both the format and the disk. Writers hand it bytes or a stream;
 * readers get a plaintext length and a stream that can start at any offset without decrypting what
 * comes before.
 *
 * <p><b>Writes are atomic.</b> Everything lands in a sibling {@code .tmp} and is moved into place,
 * so a process killed mid-write leaves the previous file — or no file — and never a half-sealed
 * one. The migrator depends on this: it is what makes "resumable" mean "run it again" instead of
 * "work out where it stopped".
 */
public class EncryptedAssetService {

  private static final Logger logger = LoggerFactory.getLogger(EncryptedAssetService.class);

  private final MasterKeyProvider masterKeyProvider;
  private final int blockSize;

  /**
   * @param masterKeyProvider wraps and unwraps the per-file data keys
   * @param blockSize plaintext bytes per sealed block; a {@code Range} over-reads at most this much
   *     at each end
   */
  public EncryptedAssetService(MasterKeyProvider masterKeyProvider, int blockSize) {
    this.masterKeyProvider = java.util.Objects.requireNonNull(masterKeyProvider, "keys");
    if (blockSize <= 0) {
      throw new IllegalArgumentException("blockSize must be positive");
    }
    this.blockSize = blockSize;
  }

  /**
   * Encrypts {@code plain} into {@code target}, atomically.
   *
   * @param target where the encrypted file lands
   * @param plain the plaintext
   * @throws IOException if the write fails
   */
  public void write(Path target, byte[] plain) throws IOException {
    try (InputStream in = new java.io.ByteArrayInputStream(plain)) {
      write(target, in, plain.length);
    }
  }

  /**
   * Encrypts a stream of known length into {@code target}, atomically.
   *
   * <p>The length is required rather than discovered because it goes in the header, and the header
   * is the AAD every block is bound to — a length written after the fact could not be authenticated
   * by the blocks that preceded it.
   *
   * @param target where the encrypted file lands
   * @param plain the plaintext stream, read exactly {@code plainLength} bytes
   * @param plainLength how many bytes {@code plain} will yield
   * @throws IOException if the write fails, or the stream is shorter than promised
   */
  public void write(Path target, InputStream plain, long plainLength) throws IOException {
    SecretKey dataKey = EnvelopeCodec.newDataKey();
    MasterKeyProvider.WrappedKey wrapped = masterKeyProvider.wrap(dataKey);
    byte[] noncePrefix = EnvelopeCodec.newNoncePrefix();
    byte[] headerBytes = EnvelopeCodec.writeHeader(blockSize, plainLength, wrapped, noncePrefix);
    EnvelopeHeader header =
        new EnvelopeHeader(
            EnvelopeHeader.VERSION,
            blockSize,
            plainLength,
            wrapped.keyId(),
            wrapped.wrapped(),
            noncePrefix,
            headerBytes.length);

    Path parent = target.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path temp = Files.createTempFile(parent, ".orz-", ".tmp");
    try {
      try (OutputStream out = Files.newOutputStream(temp)) {
        out.write(headerBytes);
        byte[] buffer = new byte[blockSize];
        for (int index = 0; index < header.blockCount(); index++) {
          int expected = header.plainBytesInBlock(index);
          int read = plain.readNBytes(buffer, 0, expected);
          if (read != expected) {
            throw new IOException(
                "stream ended after " + (index * (long) blockSize + read) + " of " + plainLength);
          }
          out.write(EnvelopeCodec.sealBlock(dataKey, header, index, buffer, read));
        }
      }
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /**
   * Whether this file is encrypted, read from its first bytes.
   *
   * @param file any file
   * @return whether it opens with the envelope magic
   */
  public boolean isEncrypted(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      return EnvelopeCodec.startsWithMagic(in.readNBytes(EnvelopeHeader.MAGIC.length));
    } catch (IOException unreadable) {
      return false;
    }
  }

  /**
   * The plaintext length of an encrypted file, without decrypting a byte of it.
   *
   * @param file an encrypted file
   * @return the length its reader will produce
   * @throws IOException if the header cannot be read
   */
  public long plainLength(Path file) throws IOException {
    try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
      return EnvelopeCodec.readHeader(in).plainLength();
    }
  }

  /**
   * Opens an encrypted file for reading from the start.
   *
   * @param file an encrypted file
   * @return a plaintext stream
   * @throws IOException if the header cannot be read
   */
  public InputStream read(Path file) throws IOException {
    return read(file, 0);
  }

  /**
   * Opens an encrypted file positioned at a plaintext offset.
   *
   * <p><b>This is the method the {@code Range} problem needed.</b> It seeks to the block containing
   * {@code offset}, decrypts that block, and discards the bytes before the offset — so serving the
   * last second of a video reads one block instead of the file. Without block addressing, AES-GCM
   * would force a full decrypt for every range, because its tag covers the whole message.
   *
   * @param file an encrypted file
   * @param offset the plaintext byte to start at
   * @return a plaintext stream beginning at {@code offset}
   * @throws IOException if the header cannot be read
   */
  public InputStream read(Path file, long offset) throws IOException {
    InputStream raw = new BufferedInputStream(Files.newInputStream(file));
    EnvelopeHeader header;
    try {
      header = EnvelopeCodec.readHeader(raw);
    } catch (IOException notAnEnvelope) {
      raw.close();
      throw notAnEnvelope;
    }
    SecretKey dataKey = masterKeyProvider.unwrap(header.keyId(), header.wrapped());
    raw.close();
    return new BlockDecryptingInputStream(file, header, dataKey, offset);
  }

  /**
   * Reads a file whole, decrypting it if it is an envelope.
   *
   * <p>The one call every reader that is not serving HTTP should use. It is the read half of the
   * cutover: while {@code acceptPlaintext} is on, a file that predates the migration is still
   * readable; once off, it is not readable at all rather than readable as plaintext.
   *
   * @param file the file
   * @param acceptPlaintext whether an unconverted file may still be read
   * @return the plaintext
   * @throws IOException if it cannot be read, or is plaintext and no longer tolerated
   */
  public byte[] readAllBytes(Path file, boolean acceptPlaintext) throws IOException {
    if (isEncrypted(file)) {
      try (InputStream in = read(file)) {
        return in.readAllBytes();
      }
    }
    if (acceptPlaintext) {
      return Files.readAllBytes(file);
    }
    throw new IOException(
        file + " is not encrypted and accept-plaintext is off; run the asset migration");
  }

  /**
   * Re-encrypts a plaintext file in place, atomically, and reports whether it did anything.
   *
   * @param file the file to convert
   * @return {@code true} if it was plaintext and is now encrypted; {@code false} if it already was
   * @throws IOException if the conversion fails — in which case the original is untouched
   */
  public boolean encryptInPlace(Path file) throws IOException {
    if (isEncrypted(file)) {
      return false;
    }
    long length = Files.size(file);
    try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
      write(file, in, length);
    }
    logger.debug("Encrypted {} ({} bytes)", file.getFileName(), length);
    return true;
  }
}
