package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.LawImportEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** The imports of the law corpus. */
public interface LawImportRepository extends JpaRepository<LawImportEntity, UUID> {

  /**
   * The newest import of a dataset with a given status and mode.
   *
   * @param sourceKey the source
   * @param datasetUrl the dataset address
   * @param mode {@code AUTOMATED} or {@code MANUAL}
   * @param status {@code SUCCEEDED}, for the last good read
   * @return the import
   */
  Optional<LawImportEntity> findFirstBySourceKeyAndDatasetUrlAndModeAndStatusOrderByStartedAtDesc(
      String sourceKey, String datasetUrl, String mode, String status);

  /**
   * The imports, newest first.
   *
   * @param page the page
   * @return the rows
   */
  List<LawImportEntity> findAllByOrderByStartedAtDesc(Pageable page);
}
