package com.krizaka.sovkora.conversation.domain.model.reader;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a reader answered for one page: its text and lines, which reader read it in which version,
 * and what the reading cost and took. The page itself is not here: the door keeps none of it.
 *
 * @param text the lines joined, in reading order
 * @param lines the lines, each with its box as fractions of the page and its confidence
 * @param reader the reader that answered
 * @param version that reader's exact version
 * @param generative whether that reader is a generative model
 * @param pageHash the SHA-256 of the page
 * @param key the idempotency key: the page hash, the reader and its version
 * @param costMicros what the page cost, in millionths of a US dollar
 * @param latencyMs how long the read took, fallback included
 * @param fallbackFrom the default reader that failed when the fallback answered, else null
 */
public record DocumentReading(
    String text,
    List<Line> lines,
    String reader,
    String version,
    boolean generative,
    String pageHash,
    String key,
    long costMicros,
    long latencyMs,
    @Nullable String fallbackFrom) {

  /** Copies the lines and refuses a measurement that cannot be one. */
  public DocumentReading {
    lines = List.copyOf(lines);
    if (reader.isBlank() || version.isBlank()) {
      throw new IllegalArgumentException("a reading names its reader and version");
    }
    if (costMicros < 0 || latencyMs < 0) {
      throw new IllegalArgumentException("a reading's cost and latency are not negative");
    }
  }

  /**
   * One line read.
   *
   * @param text the line
   * @param left the box's left edge, a fraction of the page width
   * @param top the box's top edge, a fraction of the page height
   * @param right the box's right edge
   * @param bottom the box's bottom edge
   * @param confidence the reader's confidence, 0 to 1
   */
  public record Line(
      String text, double left, double top, double right, double bottom, double confidence) {}
}
