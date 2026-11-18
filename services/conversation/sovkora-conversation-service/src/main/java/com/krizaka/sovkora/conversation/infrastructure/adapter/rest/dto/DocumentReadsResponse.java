package com.krizaka.sovkora.conversation.infrastructure.adapter.rest.dto;

import com.krizaka.sovkora.conversation.domain.model.reader.DocumentReading;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The answer of {@code POST /api/v1/document-reads}: one reading per page, in order, in the names
 * of the reader worker's endpoint.
 *
 * @param readings the readings
 */
public record DocumentReadsResponse(List<Reading> readings) {

  /**
   * @param readings the domain readings
   * @return their answer
   */
  public static DocumentReadsResponse of(List<DocumentReading> readings) {
    return new DocumentReadsResponse(readings.stream().map(Reading::of).toList());
  }

  /** One reading. */
  public record Reading(
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

    static Reading of(DocumentReading reading) {
      return new Reading(
          reading.text(),
          reading.lines().stream()
              .map(
                  line ->
                      new Line(
                          line.text(),
                          new Box(line.left(), line.top(), line.right(), line.bottom()),
                          line.confidence()))
              .toList(),
          reading.reader(),
          reading.version(),
          reading.generative(),
          reading.pageHash(),
          reading.key(),
          reading.costMicros(),
          reading.latencyMs(),
          reading.fallbackFrom());
    }
  }

  /** One line, its box and the reader's confidence. */
  public record Line(String text, Box box, double confidence) {}

  /** A box, as fractions of the page. */
  public record Box(double left, double top, double right, double bottom) {}
}
