package com.krizaka.sovkora.tools.application.port.law;

import com.krizaka.sovkora.tools.domain.model.law.StoredRetrieval;
import java.time.LocalDate;
import java.util.Optional;

/**
 * {@code law_retrieval}: the cache of stage 2 (ADR-089 §2 and §7). A retrieval is computed once per
 * jurisdiction, kind, language and as-of bucket, and again only when a cited article changes or
 * after {@code law.retrieval.max-age-days}.
 */
public interface LawRetrievalCache {

  /**
   * The cached retrieval of a key, stale or not.
   *
   * @param jurisdiction the jurisdiction
   * @param kindId the kind of document
   * @param language {@code fr} or {@code en}: claims are written in one language and never served
   *     for the other
   * @param asOfBucket the {@code in_force_from} of the newest article used
   * @return the row, if any
   */
  Optional<StoredRetrieval> find(
      String jurisdiction, String kindId, String language, LocalDate asOfBucket);

  /**
   * Stores a retrieval, replacing the row of the same key (a stale one included, which becomes
   * fresh). Two calls computing the same key at once leave one row and fail neither.
   *
   * @param retrieval the retrieval, {@code stale} false
   */
  void save(StoredRetrieval retrieval);
}
