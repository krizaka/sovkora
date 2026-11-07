package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawInstrumentEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The acts and regulations of the law corpus. */
public interface LawInstrumentRepository extends JpaRepository<LawInstrumentEntity, UUID> {

  /**
   * An instrument by its natural key.
   *
   * @param jurisdiction the jurisdiction
   * @param code the instrument code
   * @return the instrument
   */
  Optional<LawInstrumentEntity> findByJurisdictionAndCode(String jurisdiction, String code);

  /**
   * Creates an instrument unless it exists. Two imports starting together cannot both create it.
   *
   * @param id the id for a new row
   * @param jurisdiction the jurisdiction
   * @param code the instrument code
   * @param officialUrlTemplate the template of the citation address
   * @param sourceKey the {@code research_source} it is recorded under
   * @return 1 when created, 0 when it already existed
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO law_instrument (id, jurisdiction, code, official_url_template, source_key)"
              + " VALUES (:id, :jurisdiction, :code, :officialUrlTemplate, :sourceKey)"
              + " ON CONFLICT (jurisdiction, code) DO NOTHING",
      nativeQuery = true)
  int createIfAbsent(
      @Param("id") UUID id,
      @Param("jurisdiction") String jurisdiction,
      @Param("code") String code,
      @Param("officialUrlTemplate") String officialUrlTemplate,
      @Param("sourceKey") String sourceKey);
}
