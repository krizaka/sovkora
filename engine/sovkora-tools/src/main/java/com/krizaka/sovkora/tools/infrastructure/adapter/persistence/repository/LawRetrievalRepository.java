package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawRetrievalEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawRetrievalId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code law_retrieval}. */
public interface LawRetrievalRepository extends JpaRepository<LawRetrievalEntity, LawRetrievalId> {

  /**
   * Inserts a retrieval or replaces the row of the same key, in one statement: two calls computing
   * the same key at once leave one row and fail neither.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO law_retrieval (jurisdiction, kind_id, language, as_of_bucket, articles, claims,"
              + " run_id, created_at, stale) VALUES (:jurisdiction, :kindId, :language, :asOfBucket,"
              + " CAST(:articles AS jsonb), CAST(:claims AS jsonb), :runId, :createdAt, FALSE)"
              + " ON CONFLICT (jurisdiction, kind_id, language, as_of_bucket) DO UPDATE SET"
              + " articles = EXCLUDED.articles, claims = EXCLUDED.claims, run_id = EXCLUDED.run_id,"
              + " created_at = EXCLUDED.created_at, stale = FALSE",
      nativeQuery = true)
  int upsert(
      @Param("jurisdiction") String jurisdiction,
      @Param("kindId") String kindId,
      @Param("language") String language,
      @Param("asOfBucket") LocalDate asOfBucket,
      @Param("articles") String articles,
      @Param("claims") String claims,
      @Param("runId") UUID runId,
      @Param("createdAt") Instant createdAt);

  /**
   * Marks stale every retrieval of a jurisdiction that cites a version of this article, whatever
   * its hash: the cited text is no longer the only one.
   */
  @Modifying
  @Query(
      value =
          "UPDATE law_retrieval SET stale = TRUE WHERE jurisdiction = :jurisdiction"
              + " AND stale = FALSE AND articles @> CAST(:probe AS jsonb)",
      nativeQuery = true)
  int markStaleCiting(@Param("jurisdiction") String jurisdiction, @Param("probe") String probe);
}
