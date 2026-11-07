package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.ValidationPipelineConfigEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for validation pipeline configuration entries.
 *
 * <p>Queries use {@code String} for step type lookup to maintain persistence-layer isolation from
 * core domain enums (§1.2 Module Separation Invariant).
 *
 * @since 1.1.0
 */
public interface ValidationPipelineConfigJpaRepository
    extends JpaRepository<ValidationPipelineConfigEntity, UUID> {

  /**
   * Retrieves all validation pipeline configurations ordered by execution order.
   *
   * @return Ordered list of validation pipeline config entities.
   */
  List<ValidationPipelineConfigEntity> findAllByOrderByExecutionOrderAsc();

  /**
   * Finds a single validation pipeline configuration by its step type string.
   *
   * @param stepType The validation step type name (e.g., "STRUCTURAL_A", "TDR_D").
   * @return Optional containing the entity if found.
   */
  Optional<ValidationPipelineConfigEntity> findByStepType(String stepType);
}
