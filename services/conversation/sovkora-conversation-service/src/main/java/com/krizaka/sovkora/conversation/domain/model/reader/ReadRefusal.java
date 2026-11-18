package com.krizaka.sovkora.conversation.domain.model.reader;

import java.util.Arrays;

/**
 * Why pages were not read, each with the HTTP status the door answers: the worker's codes ({@code
 * workers/reader/document_reader/port.py}), never its message, which can quote the page.
 */
public enum ReadRefusal {
  /** A page or the request is malformed. */
  INPUT_INVALID(400),
  /** More pages than the routing's page budget. */
  BUDGET_EXHAUSTED(413),
  /** The reader refused the page. */
  READER_REFUSED(422),
  /** A routing row is out of its bounds: an operator's setting to fix, not the caller's fault. */
  ROUTING_INVALID(500),
  /** No reader could answer now. */
  READER_UNAVAILABLE(503),
  /** The reader took longer than the routing allows. */
  READER_TIMEOUT(504);

  private final int status;

  ReadRefusal(int status) {
    this.status = status;
  }

  /**
   * @return the HTTP status of this refusal
   */
  public int status() {
    return status;
  }

  /**
   * @param code a code the worker answered
   * @return its refusal; a code that is not one (the worker's own token refusal among them) is
   *     {@link #READER_UNAVAILABLE}: the door, not its caller, is misconfigured
   */
  public static ReadRefusal fromCode(String code) {
    return Arrays.stream(values())
        .filter(refusal -> refusal.name().equals(code))
        .findFirst()
        .orElse(READER_UNAVAILABLE);
  }
}
