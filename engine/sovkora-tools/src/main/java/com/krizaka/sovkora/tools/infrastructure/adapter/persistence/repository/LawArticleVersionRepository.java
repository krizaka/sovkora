package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawArticleVersionEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawArticleVersionId;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The dated versions of the articles of the law corpus. */
public interface LawArticleVersionRepository
    extends JpaRepository<LawArticleVersionEntity, LawArticleVersionId> {

  /**
   * Every version of an instrument that is not closed.
   *
   * @param instrumentId the instrument
   * @return the open versions, all references and languages
   */
  List<LawArticleVersionEntity> findByIdInstrumentIdAndInForceToIsNull(UUID instrumentId);

  /**
   * The open version of an article, locked for the transaction: a second import waits here, and
   * then finds the version already closed.
   *
   * @param instrumentId the instrument
   * @param ref the article reference
   * @param language the language
   * @return the open version, or empty when there is none (or another import closed it first)
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select v from LawArticleVersionEntity v where v.id.instrumentId = :instrumentId"
          + " and v.id.ref = :ref and v.id.language = :language and v.inForceTo is null")
  Optional<LawArticleVersionEntity> lockOpen(
      @Param("instrumentId") UUID instrumentId,
      @Param("ref") String ref,
      @Param("language") String language);

  /**
   * The newest version that took effect on or before a day. Versions of an article never overlap,
   * so it is the only one that can be in force then; the caller checks that it was not closed.
   *
   * @param instrumentId the instrument
   * @param ref the article reference
   * @param language the language
   * @param asOf the day
   * @return the version
   */
  Optional<LawArticleVersionEntity>
      findFirstByIdInstrumentIdAndIdRefAndIdLanguageAndIdInForceFromLessThanEqualOrderByIdInForceFromDesc(
          UUID instrumentId, String ref, String language, LocalDate asOf);

  /**
   * Records that open versions were confirmed by an import.
   *
   * @param instrumentId the instrument
   * @param language the language
   * @param refs the references confirmed
   * @param seenAt the import instant
   * @return the rows updated
   */
  @Modifying
  @Query(
      "update LawArticleVersionEntity v set v.lastSeenAt = :seenAt"
          + " where v.id.instrumentId = :instrumentId and v.id.language = :language"
          + " and v.id.ref in :refs and v.inForceTo is null")
  int touchOpen(
      @Param("instrumentId") UUID instrumentId,
      @Param("language") String language,
      @Param("refs") Collection<String> refs,
      @Param("seenAt") Instant seenAt);

  /**
   * The versions of an article, newest first (the queue's history; metadata only is read from it).
   *
   * @param instrumentId the instrument
   * @param ref the article reference
   * @param language {@code fr} or {@code en}
   * @return the versions
   */
  List<LawArticleVersionEntity> findByIdInstrumentIdAndIdRefAndIdLanguageOrderByIdInForceFromDesc(
      UUID instrumentId, String ref, String language);

  /**
   * Per instrument: the articles with an open version, and every version.
   *
   * @return rows of {@code [instrumentId, open, total]}
   */
  @Query(
      "select v.id.instrumentId, sum(case when v.inForceTo is null then 1L else 0L end), count(v)"
          + " from LawArticleVersionEntity v group by v.id.instrumentId")
  List<Object[]> countsByInstrument();
}
