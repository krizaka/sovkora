package com.krizaka.sovkora.tools.domain.model.law;

import java.util.Objects;

/**
 * The answer of the law read API (issue #89).
 *
 * @param outcome what was found
 * @param readId the identifier the read was metered under; null when nothing was held
 * @param version the version in force; present only when {@code outcome} is {@link Outcome#FOUND}
 * @param quote the check of the quote asked; null when none was asked or nothing was found
 */
public record LawArticleRead(
    Outcome outcome, String readId, StatuteVersion version, QuoteCheck quote) {

  /** What a read can answer. */
  public enum Outcome {
    /** The version in force on the day asked, its hash checked. Charged: one {@code CALL}. */
    FOUND,
    /** No version is known for that day. Not charged. */
    LAW_VERSION_UNKNOWN,
    /** {@code law.corpus.enabled} is false. Nothing held. */
    LAW_CORPUS_DISABLED,
    /** The stored text no longer gives its recorded hash: no text is served. Not charged. */
    LAW_INTEGRITY_ERROR,
    /** The caller's budget or credits cannot hold the call. Nothing read. */
    LAW_BUDGET_EXCEEDED,
    /** No meter is wired: a read is never served unmetered. Nothing read. */
    LAW_METERING_UNAVAILABLE
  }

  /** Checks that a version comes with {@link Outcome#FOUND}, and only with it. */
  public LawArticleRead {
    Objects.requireNonNull(outcome, "outcome");
    if ((outcome == Outcome.FOUND) != (version != null)) {
      throw new IllegalArgumentException("a version is returned exactly when one was found");
    }
    if (quote != null && version == null) {
      throw new IllegalArgumentException("a quote is checked only against a text served");
    }
  }

  /**
   * A refusal or an absence: no text, no quote check.
   *
   * @param outcome any outcome but {@link Outcome#FOUND}
   * @param readId the identifier the read was metered under, or null
   * @return the answer
   */
  public static LawArticleRead without(Outcome outcome, String readId) {
    return new LawArticleRead(outcome, readId, null, null);
  }
}
