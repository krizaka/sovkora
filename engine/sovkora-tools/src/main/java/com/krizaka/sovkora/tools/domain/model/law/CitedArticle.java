package com.krizaka.sovkora.tools.domain.model.law;

import java.util.Objects;

/**
 * An article a retrieval used, as the cache keeps it: enough to see that the corpus still holds
 * this very text.
 *
 * @param instrument the instrument code
 * @param ref the article reference
 * @param language {@code fr} or {@code en}
 * @param contentHash SHA-256 of the article text, in lowercase hex
 */
public record CitedArticle(String instrument, String ref, String language, String contentHash) {

  /** Checks that every part is present. */
  public CitedArticle {
    Objects.requireNonNull(instrument, "instrument");
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(contentHash, "contentHash");
  }
}
