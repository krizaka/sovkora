package com.krizaka.sovkora.assets.application.service;

import com.krizaka.sovkora.assets.domain.model.EnvelopeHeader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.crypto.SecretKey;

/**
 * A plaintext stream over a block-sealed file, able to start and skip in constant time.
 *
 * <p>The reason the format has blocks at all. {@code ResourceRegionHttpMessageConverter} serves an
 * HTTP {@code Range} by opening the resource and calling {@code skip(start)}; over a single sealed
 * message that would decrypt everything before the range, so a seek to the end of a video would
 * cost the whole video. Here a skip lands on a block boundary and decrypts one block.
 *
 * <p>Not thread-safe, and not meant to be: one request, one stream.
 */
final class BlockDecryptingInputStream extends InputStream {

  private final EnvelopeHeader header;
  private final SecretKey dataKey;
  private final SeekableByteChannel channel;

  private byte[] block = new byte[0];
  private int blockIndex = -1;
  private int positionInBlock;
  private long plainPosition;

  BlockDecryptingInputStream(Path file, EnvelopeHeader header, SecretKey dataKey, long offset)
      throws IOException {
    this.header = header;
    this.dataKey = dataKey;
    this.channel = Files.newByteChannel(file, StandardOpenOption.READ);
    if (offset > 0) {
      seek(offset);
    }
  }

  /** Positions the stream at a plaintext offset by loading the one block that contains it. */
  private void seek(long offset) throws IOException {
    long clamped = Math.min(Math.max(0, offset), header.plainLength());
    this.plainPosition = clamped;
    if (clamped >= header.plainLength()) {
      this.blockIndex = header.blockCount();
      this.block = new byte[0];
      this.positionInBlock = 0;
      return;
    }
    int target = (int) (clamped / header.blockSize());
    loadBlock(target);
    this.positionInBlock = (int) (clamped % header.blockSize());
  }

  private void loadBlock(int index) throws IOException {
    int sealedLength = header.plainBytesInBlock(index) + EnvelopeHeader.TAG_LENGTH;
    ByteBuffer buffer = ByteBuffer.allocate(sealedLength);
    channel.position(header.offsetOfBlock(index));
    while (buffer.hasRemaining()) {
      if (channel.read(buffer) < 0) {
        throw new IOException("truncated asset: block " + index + " is short");
      }
    }
    this.block = EnvelopeCodec.openBlock(dataKey, header, index, buffer.array(), sealedLength);
    this.blockIndex = index;
    this.positionInBlock = 0;
  }

  @Override
  public int read() throws IOException {
    byte[] one = new byte[1];
    int read = read(one, 0, 1);
    return read < 0 ? -1 : one[0] & 0xFF;
  }

  @Override
  public int read(byte[] destination, int off, int len) throws IOException {
    if (len == 0) {
      return 0;
    }
    if (plainPosition >= header.plainLength()) {
      return -1;
    }
    if (positionInBlock >= block.length) {
      int next = (int) (plainPosition / header.blockSize());
      if (next >= header.blockCount()) {
        return -1;
      }
      loadBlock(next);
    }
    int available = block.length - positionInBlock;
    int count = Math.min(len, available);
    System.arraycopy(block, positionInBlock, destination, off, count);
    positionInBlock += count;
    plainPosition += count;
    return count;
  }

  /**
   * Skips by seeking, not by decrypting.
   *
   * <p>{@code InputStream.skip} is allowed to read and discard, and the default implementation
   * does. Here it repositions, which is the difference between a range request costing one block
   * and costing the file.
   */
  @Override
  public long skip(long n) throws IOException {
    if (n <= 0) {
      return 0;
    }
    long before = plainPosition;
    seek(plainPosition + n);
    return plainPosition - before;
  }

  @Override
  public int available() {
    return (int) Math.min(Integer.MAX_VALUE, header.plainLength() - plainPosition);
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }
}
