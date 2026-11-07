package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An article version the pack holds for offline verification ({@code document-validation/law/},
 * read by the worker's {@code citations.py}): a snapshot of the corpus row, never a second corpus.
 *
 * @param instrument the instrument code
 * @param ref the article reference
 * @param language {@code fr} or {@code en}
 * @param inForceFrom the day this version took effect
 * @param contentHash the SHA-256 of the text
 * @param officialUrl the publisher's page
 * @param text the article text
 * @param importId the {@code law_import} that brought this text in
 */
public record PinnedArticle(
    String instrument,
    String ref,
    String language,
    LocalDate inForceFrom,
    String contentHash,
    String officialUrl,
    String text,
    UUID importId) {

  /**
   * The file name the pack holds it under, such as {@code CCQ-1991-1931-fr-1999-04-01.json}.
   *
   * @return the name
   */
  public String fileName() {
    return instrument + "-" + ref + "-" + language + "-" + inForceFrom + ".json";
  }
}
