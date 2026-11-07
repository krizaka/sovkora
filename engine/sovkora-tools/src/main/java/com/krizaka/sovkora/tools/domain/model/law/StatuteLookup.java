package com.krizaka.sovkora.tools.domain.model.law;

import java.util.Objects;

/**
 * The answer of {@code lookupStatute}: the version in force on the day asked, or the reason there
 * is none.
 *
 * @param outcome what was found
 * @param version the version in force; present only when {@code outcome} is {@link Outcome#FOUND}
 */
public record StatuteLookup(Outcome outcome, StatuteVersion version) {

  /** What a lookup can answer. */
  public enum Outcome {
    /** A version is in force on the day asked. */
    FOUND,
    /**
     * The corpus holds no version in force on that day: the article is unknown, was imported only
     * after that day, or was not in force then. A rule built on it is {@code
     * INSUFFICIENT_EVIDENCE}, never a verdict read from a later text.
     */
    LAW_VERSION_UNKNOWN,
    /** {@code law.corpus.enabled} is false: the corpus is off. */
    LAW_CORPUS_DISABLED,
    /**
     * The stored text no longer gives the hash recorded with it (changed after its import): no text
     * is returned, fail closed (issue #89).
     */
    LAW_INTEGRITY_ERROR
  }

  /** Checks that a version comes with {@link Outcome#FOUND}, and only with it. */
  public StatuteLookup {
    Objects.requireNonNull(outcome, "outcome");
    if ((outcome == Outcome.FOUND) != (version != null)) {
      throw new IllegalArgumentException("a version is returned exactly when one was found");
    }
  }

  /**
   * A version was found.
   *
   * @param version the version in force
   * @return the answer
   */
  public static StatuteLookup found(StatuteVersion version) {
    return new StatuteLookup(Outcome.FOUND, version);
  }

  /**
   * No version is known for the day asked.
   *
   * @return the answer
   */
  public static StatuteLookup unknown() {
    return new StatuteLookup(Outcome.LAW_VERSION_UNKNOWN, null);
  }

  /**
   * The corpus is off.
   *
   * @return the answer
   */
  public static StatuteLookup disabled() {
    return new StatuteLookup(Outcome.LAW_CORPUS_DISABLED, null);
  }

  /**
   * The stored text failed its hash check.
   *
   * @return the answer, with no text
   */
  public static StatuteLookup integrityError() {
    return new StatuteLookup(Outcome.LAW_INTEGRITY_ERROR, null);
  }
}
