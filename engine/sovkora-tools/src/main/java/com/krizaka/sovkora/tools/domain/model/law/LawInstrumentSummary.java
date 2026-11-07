package com.krizaka.sovkora.tools.domain.model.law;

/**
 * An instrument of the corpus as the review queue lists it.
 *
 * @param jurisdiction the jurisdiction
 * @param code the instrument code
 * @param titleFr its French title, when known
 * @param titleEn its English title, when known
 * @param sourceKey the source it is read from
 * @param articles the articles with a version in force (open)
 * @param versions every version it holds
 */
public record LawInstrumentSummary(
    String jurisdiction,
    String code,
    String titleFr,
    String titleEn,
    String sourceKey,
    long articles,
    long versions) {}
