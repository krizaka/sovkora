package com.krizaka.sovkora.tools.domain.model.research;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of a fetch: the document, or the reason there is none. Never both.
 *
 * @param document what was read, when the fetch was made or served from the cache
 * @param refusal why nothing was read
 * @param cacheHit whether the document came from the cache without an HTTP call
 */
public record FetchResult(
    Optional<FetchedDocument> document, Optional<FetchRefusal> refusal, boolean cacheHit) {

  /** Validates that exactly one of document and refusal is present. */
  public FetchResult {
    Objects.requireNonNull(document, "document must not be null");
    Objects.requireNonNull(refusal, "refusal must not be null");
    if (document.isPresent() == refusal.isPresent()) {
      throw new IllegalArgumentException(
          "a fetch result is a document or a refusal, never both or neither");
    }
  }

  /**
   * A document read now or from the cache.
   *
   * @param document what was read
   * @param cacheHit whether no HTTP call was made
   * @return the result
   */
  public static FetchResult of(FetchedDocument document, boolean cacheHit) {
    return new FetchResult(Optional.of(document), Optional.empty(), cacheHit);
  }

  /**
   * A refused fetch.
   *
   * @param reason why
   * @return the result
   */
  public static FetchResult refused(FetchRefusal reason) {
    return new FetchResult(Optional.empty(), Optional.of(reason), false);
  }
}
