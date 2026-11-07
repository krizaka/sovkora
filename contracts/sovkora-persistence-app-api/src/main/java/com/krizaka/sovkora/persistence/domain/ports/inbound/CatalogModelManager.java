package com.krizaka.sovkora.persistence.domain.ports.inbound;

import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import java.util.List;
import java.util.Optional;

/** Inbound port contract for managing the framework's AI model catalog dynamically. */
public interface CatalogModelManager {

  /**
   * Retrieves all model definitions in the catalog.
   *
   * @return A list of all CatalogModelDto.
   */
  List<CatalogModelDto> getAllModels();

  /**
   * Retrieves model definitions by category.
   *
   * @param category The category (e.g. speech, image, video, vision).
   * @return A list of CatalogModelDto.
   */
  List<CatalogModelDto> getModelsByCategory(String category);

  /**
   * Retrieves the dynamic default model for a given category.
   *
   * @param category The category (e.g. speech, image, video, vision, audio).
   * @return An Optional containing the default CatalogModelDto, or empty if none is set.
   */
  Optional<CatalogModelDto> getDefaultModelByCategory(String category);

  /**
   * Saves or updates a model definition in the catalog.
   *
   * @param dto The CatalogModelDto to persist.
   * @return The persisted CatalogModelDto.
   */
  CatalogModelDto saveModel(CatalogModelDto dto);

  /**
   * Deletes a model definition by its ID.
   *
   * @param id The ID of the model to delete.
   */
  void deleteModel(Integer id);

  /**
   * Retrieves all available AI provider names from the database.
   *
   * @return A list of unique provider name strings.
   */
  List<String> getAllProviders();

  /**
   * Resolves the base URL for the given provider name dynamically.
   *
   * @param providerName The unique provider name (e.g. "ollama", "localai").
   * @return The base URL endpoint string, or null if not found.
   */
  String getProviderBaseUrl(String providerName);

  /**
   * Resolves the API key of the given provider ({@code ai_providers.api_key}): a hosted
   * OpenAI-compatible provider (serverless inference) needs it on every call; a local one has none.
   *
   * @param providerName The unique provider name (e.g. "do-inference").
   * @return The provider's key, or null when it has none or is unknown.
   */
  default String getProviderApiKey(String providerName) {
    return null;
  }
}
