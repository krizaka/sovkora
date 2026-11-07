package com.krizaka.sovkora.core.application.service;

import com.krizaka.sovkora.core.domain.model.CatalogModelInfo;
import com.krizaka.sovkora.core.domain.ports.inbound.CatalogModelService;
import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Package-private implementation of the CatalogModelService inbound port. Delegates to the
 * out-of-boundary persistence package. Follows ERR-105 (Interface-Driven Boundaries).
 */
public class DefaultCatalogModelService implements CatalogModelService {

  private final CatalogModelManager catalogModelManager;

  public DefaultCatalogModelService(CatalogModelManager catalogModelManager) {
    this.catalogModelManager =
        Objects.requireNonNull(catalogModelManager, "CatalogModelManager cannot be null");
  }

  @Override
  public List<CatalogModelInfo> getAllModels() {
    return catalogModelManager.getAllModels().stream()
        .map(DefaultCatalogModelService::toInfo)
        .toList();
  }

  @Override
  public List<CatalogModelInfo> getModelsByCategory(String category) {
    return catalogModelManager.getModelsByCategory(category).stream()
        .map(DefaultCatalogModelService::toInfo)
        .toList();
  }

  @Override
  public Optional<CatalogModelInfo> getDefaultModelByCategory(String category) {
    return catalogModelManager
        .getDefaultModelByCategory(category)
        .map(DefaultCatalogModelService::toInfo);
  }

  @Override
  public CatalogModelInfo saveModel(CatalogModelInfo dto) {
    return toInfo(catalogModelManager.saveModel(toDto(dto)));
  }

  @Override
  public void deleteModel(Integer id) {
    catalogModelManager.deleteModel(id);
  }

  private static CatalogModelInfo toInfo(CatalogModelDto dto) {
    return new CatalogModelInfo(
        dto.id(),
        dto.modelName(),
        dto.modelLabel(),
        dto.category(),
        dto.options(),
        dto.isDefault(),
        dto.providerName(),
        dto.maxSteps(),
        dto.recommendedFps(),
        dto.supportedHardware());
  }

  private static CatalogModelDto toDto(CatalogModelInfo info) {
    return new CatalogModelDto(
        info.id(),
        info.modelName(),
        info.modelLabel(),
        info.category(),
        info.options(),
        info.isDefault(),
        info.providerName(),
        info.maxSteps(),
        info.recommendedFps(),
        info.supportedHardware());
  }
}
