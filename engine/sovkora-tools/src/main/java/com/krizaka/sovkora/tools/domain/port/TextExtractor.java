package com.krizaka.sovkora.tools.domain.port;

import java.net.URI;

/** Turns a fetched body into the text a citation can quote: the main content, no markup. */
public interface TextExtractor {

  /**
   * The extracted page.
   *
   * @param title the page title, or empty
   * @param text the main text, paragraphs separated by blank lines
   */
  record Extracted(String title, String text) {}

  /**
   * Extracts the text.
   *
   * @param body the body, as the server sent it
   * @param contentType its media type
   * @param charset the charset the server named, or {@code null} to read it from the bytes (a BOM,
   *     a {@code <meta charset>}) and fall back to UTF-8
   * @param base the page's URI
   * @return the title and text
   */
  Extracted extract(byte[] body, String contentType, String charset, URI base);
}
