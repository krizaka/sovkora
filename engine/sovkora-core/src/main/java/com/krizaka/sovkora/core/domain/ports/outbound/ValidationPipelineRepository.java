package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.ValidationPipelineConfiguration;
import java.util.List;

/**
 * Outbound port interface for validation pipeline configuration persistence.
 *
 * <p>Implemented by the persistence adapter layer to load and save the 4-tier validation matrix
 * configuration. No Spring Data JPA annotations are permitted on this interface — it is a pure
 * domain port residing in {@code sovkora-core}.
 *
 * @since 1.1.0
 */
public interface ValidationPipelineRepository {

  /**
   * Retrieves all validation pipeline step configurations ordered by execution order.
   *
   * @return Ordered list of validation pipeline configurations.
   */
  List<ValidationPipelineConfiguration> findAllOrderedByExecution();

  /**
   * Persists a single validation pipeline step configuration.
   *
   * @param config The validation step configuration to save.
   * @return The saved configuration with any generated fields populated.
   */
  ValidationPipelineConfiguration save(ValidationPipelineConfiguration config);

  /**
   * Bulk-persists a list of validation pipeline step configurations (typically after reorder or
   * toggle updates from the admin UI).
   *
   * @param configs The list of configurations to save.
   * @return The saved configurations.
   */
  List<ValidationPipelineConfiguration> saveAll(List<ValidationPipelineConfiguration> configs);
}
