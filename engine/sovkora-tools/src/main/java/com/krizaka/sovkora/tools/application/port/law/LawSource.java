package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.tools.domain.model.law.LawSnapshot;
import com.krizaka.sovkora.tools.domain.model.law.LawTarget;
import java.util.Collection;
import java.util.Optional;

/**
 * A source of consolidated statutes (Justice Canada, Données Québec, an uploaded file): the
 * importer of one publisher. It only reads; the corpus is written by the import service.
 */
public interface LawSource {

  /**
   * The {@code research_source} this source's texts are recorded under.
   *
   * @return the source key of the {@code law_import} rows
   */
  String sourceKey();

  /**
   * Whether this source publishes the law of the jurisdiction.
   *
   * @param jurisdiction {@code CA} or {@code QC}
   * @return true when {@link #fetch} can serve the targets of that jurisdiction
   */
  boolean serves(String jurisdiction);

  /**
   * The address recorded on the import row before anything is read.
   *
   * @param jurisdiction the jurisdiction, for which {@link #serves} is true
   * @return the dataset or file address
   */
  String datasetUrl(String jurisdiction);

  /**
   * The SHA-256 of the file when it is known before reading, as for an uploaded file.
   *
   * @return the digest in lowercase hex, or empty when it is known only after the read
   */
  default Optional<String> knownSha256() {
    return Optional.empty();
  }

  /**
   * Reads the targeted articles from the source, in every language it publishes them. An article
   * that the source no longer holds in force is absent from the result.
   *
   * @param jurisdiction the jurisdiction, for which {@link #serves} is true
   * @param targets the targets of that jurisdiction
   * @param knownEtag the validator of the dataset already imported; a source that can ask the
   *     publisher whether it changed does so, and answers {@code notModified}
   * @return the articles found, all languages, targeted instruments only, with the facts of the
   *     read
   */
  LawSnapshot fetch(String jurisdiction, Collection<LawTarget> targets, Optional<String> knownEtag);
}
