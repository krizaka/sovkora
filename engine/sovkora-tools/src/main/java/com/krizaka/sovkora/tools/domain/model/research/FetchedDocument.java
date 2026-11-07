package com.krizaka.sovkora.tools.domain.model.research;

import java.time.Instant;
import java.util.Objects;

/**
 * What a fetch read, stored once by URL and content hash ({@code research_document}). A citation
 * names the hash it read, so the verifier reads back the same bytes (ADR-087 §3).
 *
 * @param url the final URL, after redirects
 * @param contentHash SHA-256 (hex) of {@link #text}
 * @param fetchedAt when it was read
 * @param httpStatus the status of the final answer
 * @param contentType the media type, without parameters
 * @param bytes the size of the body read
 * @param title the page title, or empty
 * @param text the extracted text
 * @param expiresAt when the cache stops serving it
 */
public record FetchedDocument(
    String url,
    String contentHash,
    Instant fetchedAt,
    int httpStatus,
    String contentType,
    int bytes,
    String title,
    String text,
    Instant expiresAt) {

  /** Validates the document. */
  public FetchedDocument {
    Objects.requireNonNull(url, "url must not be null");
    Objects.requireNonNull(contentHash, "contentHash must not be null");
    Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (contentHash.length() != 64) {
      throw new IllegalArgumentException("contentHash must be a SHA-256 in hex");
    }
    title = title == null ? "" : title;
  }
}
