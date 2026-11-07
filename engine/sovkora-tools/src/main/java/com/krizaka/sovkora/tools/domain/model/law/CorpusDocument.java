package com.krizaka.sovkora.tools.domain.model.law;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An article of the corpus, as the {@code law-corpus} research provider returns it: the text, the
 * hash that identifies it, the day it took effect, the publisher's page a citation shows and the
 * import that brought it in.
 *
 * @param jurisdiction the jurisdiction
 * @param instrument the instrument code
 * @param ref the article reference
 * @param language {@code fr} or {@code en}
 * @param text the normalised text
 * @param contentHash SHA-256 of the text, in lowercase hex
 * @param inForceFrom the day this text took effect
 * @param officialUrl the publisher's page
 * @param importId the {@code law_import} that brought this text in
 */
public record CorpusDocument(
    String jurisdiction,
    String instrument,
    String ref,
    String language,
    String text,
    String contentHash,
    LocalDate inForceFrom,
    String officialUrl,
    UUID importId) {

  /**
   * The article as the cache cites it.
   *
   * @return instrument, reference, language and hash
   */
  public CitedArticle cited() {
    return new CitedArticle(instrument, ref, language, contentHash);
  }

  /**
   * What a reader and the verifier see as the document's title: the instrument and the article, so
   * that « art. 1931 » in a claim is found in the cited document's own reference.
   *
   * @return the title
   */
  public String title() {
    return instrument + ", art. " + ref;
  }
}
