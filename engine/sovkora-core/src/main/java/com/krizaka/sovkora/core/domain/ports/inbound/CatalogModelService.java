package com.krizaka.sovkora.core.domain.ports.inbound;

import com.krizaka.sovkora.core.domain.model.CatalogModelInfo;
import java.util.List;
import java.util.Optional;

/**
 * Public inbound port for managing AI model definitions. Follows ERR-105 (Interface-Driven
 * Boundaries).
 */
public interface CatalogModelService {

  /**
   * Retrieves all model definitions in the catalog.
   *
   * @return A list of all CatalogModelInfo.
   */
  List<CatalogModelInfo> getAllModels();

  /**
   * Retrieves model definitions by category.
   *
   * @param category The category (e.g. speech, image, video, vision).
   * @return A list of CatalogModelInfo.
   */
  List<CatalogModelInfo> getModelsByCategory(String category);

  /**
   * Retrieves the dynamic default model for a given category.
   *
   * @param category The category (e.g. speech, image, video, vision, audio).
   * @return An Optional containing the default CatalogModelInfo, or empty if none is set.
   */
  Optional<CatalogModelInfo> getDefaultModelByCategory(String category);

  /**
   * Saves or updates a model definition in the catalog.
   *
   * @param dto The CatalogModelInfo to persist.
   * @return The persisted CatalogModelInfo.
   */
  CatalogModelInfo saveModel(CatalogModelInfo dto);

  /**
   * Deletes a model definition by its ID.
   *
   * @param id The ID of the model to delete.
   */
  void deleteModel(Integer id);
}
