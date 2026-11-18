package com.krizaka.sovkora.conversation.domain.model.reader;

import java.util.Arrays;
import java.util.Optional;

/**
 * The kind of a page a product sends to be read (krizaka-internal#4198): the routing chooses a
 * reader per kind. A PDF page arrives rasterised, as {@link #PDF_SCAN}.
 */
public enum PageKind {
  PDF_SCAN("pdf-scan"),
  SCREENSHOT("screenshot"),
  PHOTO("photo");

  private final String wire;

  PageKind(String wire) {
    this.wire = wire;
  }

  /**
   * @return the name on the wire and in the routing keys ({@code reader.default.<wire>})
   */
  public String wire() {
    return wire;
  }

  /**
   * @param wire a name on the wire
   * @return its kind, or empty for a name that is not one
   */
  public static Optional<PageKind> fromWire(String wire) {
    return Arrays.stream(values()).filter(kind -> kind.wire.equals(wire)).findFirst();
  }
}
