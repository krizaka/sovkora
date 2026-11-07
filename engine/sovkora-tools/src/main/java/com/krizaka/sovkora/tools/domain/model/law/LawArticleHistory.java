package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;
import java.util.List;

/**
 * The versions of one article, and the one in force on a day.
 *
 * @param jurisdiction the jurisdiction
 * @param instrument the instrument code
 * @param ref the article reference
 * @param language {@code fr} or {@code en}
 * @param asOf the day asked
 * @param inForce the version in force on it, or null
 * @param history every version, newest first
 */
public record LawArticleHistory(
    String jurisdiction,
    String instrument,
    String ref,
    String language,
    LocalDate asOf,
    LawVersionSummary inForce,
    List<LawVersionSummary> history) {

  /** Copies the history. */
  public LawArticleHistory {
    history = List.copyOf(history);
  }
}
