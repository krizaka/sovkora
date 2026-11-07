package com.krizaka.sovkora.core.domain.model.research;

import java.util.Objects;

/**
 * Where a claim was read: the URL, the verbatim passage, the hash of the document it was read in,
 * and when (ADR-087 §3).
 *
 * @param url the URL the passage was read from
 * @param quote the verbatim passage
 * @param contentHash SHA-256 (hex) of the document's extracted text
 * @param fetchedAt when it was read, ISO-8601
 */
public record Citation(String url, String quote, String contentHash, String fetchedAt) {

  /** Validates the citation. */
  public Citation {
    Objects.requireNonNull(url, "url must not be null");
    Objects.requireNonNull(quote, "quote must not be null");
    contentHash = contentHash == null ? "" : contentHash;
    fetchedAt = fetchedAt == null ? "" : fetchedAt;
  }
}
