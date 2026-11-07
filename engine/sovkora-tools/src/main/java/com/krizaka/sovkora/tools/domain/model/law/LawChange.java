package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;

/**
 * A difference between the corpus and the official text, found by an import.
 *
 * @param kind what happened to the article
 * @param previous the stored text before the change; null when the article is new
 * @param current the text read now; null when the article was removed
 * @param detectedOn the day of the import
 */
public record LawChange(Kind kind, LawArticle previous, LawArticle current, LocalDate detectedOn) {

  /** How an article moved between two imports. */
  public enum Kind {
    /** The article is new in a target that was already imported. */
    ADDED,
    /** The text changed; {@code previous} and {@code current} are both set. */
    CHANGED,
    /** The article is no longer in force in the source. */
    REMOVED
  }

  /**
   * The article this change is about: the current text, or the last known one when it was removed.
   *
   * @return the subject article
   */
  public LawArticle subject() {
    return current != null ? current : previous;
  }
}
