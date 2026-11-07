package com.krizaka.sovkora.core.domain.model.research;

import java.util.Objects;

/**
 * A document grounded research read for one answer, as the grounding stage records it in the
 * context: the source it was read through, the URL and the hash of the text. The verifier accepts a
 * citation only when it names one of these (ADR-087 §3 — a document not read by this answer never
 * verifies, and a document never answers through another source).
 *
 * @param sourceKey the {@code research_source} key the document was read through
 * @param url the URL as cited (the one asked for, or the final one)
 * @param contentHash SHA-256 (hex) of the extracted text
 */
public record ReadDocument(String sourceKey, String url, String contentHash) {

  /** Validates the reference. */
  public ReadDocument {
    Objects.requireNonNull(sourceKey, "sourceKey must not be null");
    Objects.requireNonNull(url, "url must not be null");
    Objects.requireNonNull(contentHash, "contentHash must not be null");
  }

  /**
   * Whether a citation names this document.
   *
   * @param citation the citation
   * @return true when the URL and the hash are this document's
   */
  public boolean matches(Citation citation) {
    return url.equals(citation.url()) && contentHash.equals(citation.contentHash());
  }
}
