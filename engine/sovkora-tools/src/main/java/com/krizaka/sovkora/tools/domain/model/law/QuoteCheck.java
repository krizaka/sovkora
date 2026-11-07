package com.krizaka.sovkora.tools.domain.model.law;

/**
 * Whether a quote is in an article's text, under the corpus's own normalisation ({@link
 * ContentHash#normalise}: NFC, whitespace collapsed). Deterministic, no model: the quote is the
 * article's words or it is not; no case folding, no stemming, no paraphrase.
 */
public enum QuoteCheck {
  /** The normalised quote is a substring of the normalised article text. */
  VERIFIED,
  /** It is not. */
  NOT_IN_ARTICLE;

  /**
   * Checks a quote against a text.
   *
   * @param text the article text
   * @param quote the words claimed to be in it
   * @return {@link #VERIFIED} or {@link #NOT_IN_ARTICLE}
   * @throws IllegalArgumentException when the quote is blank once normalised
   */
  public static QuoteCheck of(String text, String quote) {
    String words = ContentHash.normalise(quote);
    if (words.isEmpty()) {
      throw new IllegalArgumentException("a quote to check is not blank");
    }
    return ContentHash.normalise(text).contains(words) ? VERIFIED : NOT_IN_ARTICLE;
  }
}
