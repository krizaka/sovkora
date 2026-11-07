package com.krizaka.sovkora.tools.domain.model.law;

/**
 * Request payload for the {@code lookupStatute} tool: one article of one instrument, in one
 * language, as it was in force on a day.
 *
 * @param jurisdiction {@code CA} (federal) or {@code QC} (Québec)
 * @param instrument the instrument code, such as {@code CCQ-1991} or {@code E-15}
 * @param reference the article reference, such as {@code 1931}
 * @param asOf the day the text must be in force, ISO 8601; today when absent
 * @param language {@code fr} or {@code en}
 */
public record LookupStatuteRequest(
    String jurisdiction, String instrument, String reference, String asOf, String language) {}
