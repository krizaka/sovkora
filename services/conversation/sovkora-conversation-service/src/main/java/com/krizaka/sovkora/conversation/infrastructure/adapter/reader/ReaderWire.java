package com.krizaka.sovkora.conversation.infrastructure.adapter.reader;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The reader worker's wire shapes (workers/reader {@code reader_server.py}): no domain type. */
final class ReaderWire {

  private ReaderWire() {}

  /** The request: the pages and the routing of this call. */
  record Ask(List<Page> pages, Map<String, String> routing) {}

  /** One page: base64 image and kind name. */
  record Page(String image, String kind) {}

  /** The answer: one reading per page, in order. */
  record Answer(List<Reading> readings) {}

  /** One reading, in the worker's names. */
  record Reading(
      String text,
      List<Line> lines,
      String reader,
      String version,
      boolean generative,
      String pageHash,
      String key,
      long costMicros,
      long latencyMs,
      @Nullable String fallbackFrom) {}

  /** One line and its box. */
  record Line(String text, Box box, double confidence) {}

  /** A box, as fractions of the page. */
  record Box(double left, double top, double right, double bottom) {}

  /** A refusal: a code, never a message. */
  record Refusal(@Nullable String code) {}
}
