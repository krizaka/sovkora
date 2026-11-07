package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.tools.domain.model.law.LawArticle;
import com.krizaka.sovkora.tools.domain.model.law.LawArticleVersion;
import com.krizaka.sovkora.tools.domain.model.law.LawImportResult;
import com.krizaka.sovkora.tools.domain.model.law.LawImportStart;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The dated, hashed corpus of the law, in the job-service tables ({@code law_instrument}, {@code
 * law_article_version}, {@code law_import}). Each article keeps every version it had; at most one
 * version per article and language is open (not closed by {@code in_force_to}) at a time.
 */
public interface LawCorpusStore {

  /**
   * Records the start of an import.
   *
   * @param start what is known before the source is read
   * @return the id of the {@code law_import} row, status {@code RUNNING}
   */
  UUID beginImport(LawImportStart start);

  /**
   * Records the end of an import.
   *
   * @param importId the row opened by {@link #beginImport}
   * @param result the status, the facts of the read and the counts
   */
  void finishImport(UUID importId, LawImportResult result);

  /**
   * The validator of the last successful automated import of a dataset.
   *
   * @param sourceKey the source
   * @param datasetUrl the dataset address
   * @return its ETag, or empty when there is none
   */
  Optional<String> lastEtag(String sourceKey, String datasetUrl);

  /**
   * The versions in force (not yet closed) of an instrument, all references and languages.
   *
   * @param jurisdiction the jurisdiction
   * @param instrument the instrument code
   * @return the open versions; empty when the instrument was never imported
   */
  List<LawArticleVersion> currentVersions(String jurisdiction, String instrument);

  /**
   * Stores an article as a new open version. The instrument is created on its first article.
   *
   * @param importId the import that read it
   * @param sourceKey the source the instrument is recorded under on its creation
   * @param article the article
   * @param seenAt the import instant
   * @throws IllegalStateException when the article already has an open version (another import won)
   */
  void add(UUID importId, String sourceKey, LawArticle article, Instant seenAt);

  /**
   * Closes the open version of an article at the day the next one takes effect, and stores that
   * next one, in one transaction. The row is locked first, so two imports cannot both replace the
   * same version.
   *
   * @param importId the import that read the new text
   * @param previous the version expected to be open
   * @param next the new text; its in-force day is after the previous one's
   * @param seenAt the import instant
   * @throws IllegalStateException when the open version is no longer {@code previous}, or when
   *     {@code next} does not take effect after it
   */
  void replace(UUID importId, LawArticle previous, LawArticle next, Instant seenAt);

  /**
   * Closes the open version of an article (the source no longer holds it in force).
   *
   * @param previous the version expected to be open
   * @param inForceTo the day it stopped being in force
   * @throws IllegalStateException when the open version is no longer {@code previous}
   */
  void close(LawArticle previous, LocalDate inForceTo);

  /**
   * Records that the open versions of these articles were confirmed by an import.
   *
   * @param jurisdiction the jurisdiction
   * @param instrument the instrument code
   * @param articles the articles confirmed (same hash)
   * @param seenAt the import instant
   */
  void touch(
      String jurisdiction, String instrument, Collection<LawArticle> articles, Instant seenAt);

  /**
   * The version of an article in force on a day: {@code in_force_from <= asOf < in_force_to}.
   *
   * @param jurisdiction the jurisdiction
   * @param instrument the instrument code
   * @param ref the article reference
   * @param language {@code fr} or {@code en}
   * @param asOf the day
   * @return the version, or empty when none is known for that day
   */
  Optional<LawArticleVersion> inForceOn(
      String jurisdiction, String instrument, String ref, String language, LocalDate asOf);
}
