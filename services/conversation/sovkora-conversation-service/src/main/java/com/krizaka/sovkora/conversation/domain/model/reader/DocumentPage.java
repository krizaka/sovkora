package com.krizaka.sovkora.conversation.domain.model.reader;

import java.util.Base64;
import java.util.Objects;

/**
 * One page to read: the image, base64 as it travels, and its kind. Checked here, so a malformed
 * page is refused at the door before a reader is called; the worker checks it again by its own
 * bounds (the page at most {@value #MAX_IMAGE_BYTES} bytes).
 *
 * @param image the page image, standard base64
 * @param kind the kind of page
 */
public record DocumentPage(String image, PageKind kind) {

  /** The worker's bound on one page (workers/reader {@code MAX_PAGE_BYTES}). */
  public static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

  /** The base64 length of a page at the bound. */
  private static final int MAX_IMAGE_CHARS = (MAX_IMAGE_BYTES + 2) / 3 * 4;

  /** Checks the page is a non-empty, well-formed base64 image within the bound. */
  public DocumentPage {
    Objects.requireNonNull(kind, "kind");
    if (image == null || image.isEmpty() || image.length() > MAX_IMAGE_CHARS) {
      throw new IllegalArgumentException("a page image is 1 to " + MAX_IMAGE_BYTES + " bytes");
    }
    try {
      Base64.getDecoder().decode(image);
    } catch (IllegalArgumentException notBase64) {
      throw new IllegalArgumentException("a page image is standard base64", notBase64);
    }
  }
}
