package com.krizaka.sovkora.conversation.domain.port;

import com.krizaka.sovkora.conversation.domain.model.reader.DocumentReading;
import com.krizaka.sovkora.conversation.domain.model.reader.PageKind;
import com.krizaka.sovkora.engine.core.domain.model.security.Actor;

/** The measurement of every page read through the door: cost and latency, never a word of it. */
public interface DocumentReadLedger {

  /**
   * Records one page read. A failure to write is not swallowed: an unmeasured read is not answered.
   *
   * @param actor who asked
   * @param kind the kind of the page
   * @param reading what the reader answered
   */
  void record(Actor actor, PageKind kind, DocumentReading reading);
}
