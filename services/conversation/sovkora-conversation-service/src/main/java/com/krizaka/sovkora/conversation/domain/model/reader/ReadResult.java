package com.krizaka.sovkora.conversation.domain.model.reader;

import java.util.List;

/** The answer of a read: one reading per page in order, or the refusal of all of them. */
public sealed interface ReadResult {

  /**
   * Every page read.
   *
   * @param readings one reading per page, in the order of the pages
   */
  record Readings(List<DocumentReading> readings) implements ReadResult {
    /** Copies the readings. */
    public Readings {
      readings = List.copyOf(readings);
    }
  }

  /**
   * No page read.
   *
   * @param refusal why
   */
  record Refused(ReadRefusal refusal) implements ReadResult {}
}
