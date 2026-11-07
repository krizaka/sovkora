package com.krizaka.sovkora.tools.domain.model.law;

/**
 * The article a rule rests on, as a ruleset pins it: one version of one article (its content hash)
 * and the passage of it the rule quotes.
 *
 * @param instrument the instrument code, such as {@code CCQ-1991}
 * @param ref the article reference, such as {@code 1931}
 * @param language {@code fr} or {@code en}
 * @param contentHash SHA-256 of the article text, in lowercase hex
 * @param quote the passage of the article the rule quotes
 */
public record LawBlock(
    String instrument, String ref, String language, String contentHash, String quote) {}
