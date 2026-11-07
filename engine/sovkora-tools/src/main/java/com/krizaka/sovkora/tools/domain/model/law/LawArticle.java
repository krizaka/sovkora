package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One article of a statute or regulation, in one language, as read from an official source on a
 * given day. Its text is normalised on construction, so its {@link #contentHash()} is stable.
 *
 * @param jurisdiction {@code CA} or {@code QC}
 * @param instrument the instrument code, such as {@code CCQ-1991} or {@code E-15}
 * @param ref the article reference, such as {@code 1942} or {@code 169(4)}
 * @param language {@code fr} or {@code en}
 * @param inForceFrom the date the text is in force, from the source itself
 * @param text the normalised text (label and subsections, without the historical note)
 * @param officialUrl the citation URL of the article on the publisher's site
 * @param sourceUrl the URL the XML was read from
 */
public record LawArticle(
    String jurisdiction,
    String instrument,
    String ref,
    String language,
    LocalDate inForceFrom,
    String text,
    String officialUrl,
    String sourceUrl) {

  public LawArticle {
    Objects.requireNonNull(jurisdiction, "jurisdiction");
    Objects.requireNonNull(instrument, "instrument");
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(inForceFrom, "inForceFrom");
    Objects.requireNonNull(officialUrl, "officialUrl");
    Objects.requireNonNull(sourceUrl, "sourceUrl");
    text = ContentHash.normalise(text);
    if (text.isEmpty()) {
      throw new IllegalArgumentException("article " + instrument + " " + ref + " has no text");
    }
  }

  /**
   * The SHA-256 of the normalised text.
   *
   * @return the content hash of this version
   */
  public ContentHash contentHash() {
    return ContentHash.of(text);
  }
}
