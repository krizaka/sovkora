package com.krizaka.sovkora.core.domain.model;

/**
 * An answer a cache gives to a turn before any inference ("the turn is answered", not "the turn is
 * refused": a refusal is a {@code PipelineShortCircuitException}).
 *
 * @param text the answer to serve; never blank
 * @param tier which tier found it, e.g. {@code EXACT} or {@code SEMANTIC}; never blank
 * @param similarity how close the match was, from 0 to 1 ({@code 1.0} for an exact match)
 */
public record CachedResponse(String text, String tier, double similarity) {

  /** The provider name a cached answer reports (no model ran). */
  public static final String PROVIDER = "cache";

  /** Compact constructor: an answer must have text, a tier and a similarity in [0, 1]. */
  public CachedResponse {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("a cached response needs text");
    }
    if (tier == null || tier.isBlank()) {
      throw new IllegalArgumentException("a cached response needs a tier");
    }
    if (similarity < 0.0 || similarity > 1.0) {
      throw new IllegalArgumentException("similarity must be within [0, 1], was: " + similarity);
    }
  }
}
