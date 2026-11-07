package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;

/**
 * The {@code law} block of a promoted rule (ADR-089 §4): the article version it pins, completed
 * from the corpus with its in-force day and official address, and the passage it quotes.
 *
 * @param instrument the instrument code
 * @param ref the article reference
 * @param language {@code fr} or {@code en}
 * @param inForceFrom the day this version took effect
 * @param contentHash the SHA-256 of its text
 * @param officialUrl the publisher's page
 * @param quote the passage of the article the rule quotes, at most {@link #MAX_QUOTE} characters
 */
public record PinnedLaw(
    String instrument,
    String ref,
    String language,
    LocalDate inForceFrom,
    String contentHash,
    String officialUrl,
    String quote) {

  /** The longest quote a ruleset's law block holds (ADR-089 §4). */
  public static final int MAX_QUOTE = 300;
}
