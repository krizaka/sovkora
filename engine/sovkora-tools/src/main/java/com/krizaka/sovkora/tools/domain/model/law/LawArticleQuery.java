package com.krizaka.sovkora.tools.domain.model.law;

/**
 * A question to the law read API (issue #89), as the caller sent it: every part is checked by
 * {@code LawArticleReadService}, none is guessed.
 *
 * @param jurisdiction {@code CA} or {@code QC}
 * @param instrument the instrument code, such as {@code CCQ-1991}
 * @param ref the article reference, such as {@code 1931} or {@code 169(4)}
 * @param language {@code fr} or {@code en}
 * @param asOf the day the text must be in force, ISO {@code yyyy-MM-dd}; required, never defaulted
 * @param quote words to check against the article text; null when none is asked
 */
public record LawArticleQuery(
    String jurisdiction,
    String instrument,
    String ref,
    String language,
    String asOf,
    String quote) {}
