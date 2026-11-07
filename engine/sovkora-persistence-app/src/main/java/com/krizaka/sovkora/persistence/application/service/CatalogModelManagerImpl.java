package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.model.CatalogModelDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.AiProviderEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.CatalogModelEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.AiProviderRepository;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.CatalogModelRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Package-private implementation of the CatalogModelManager inbound port. */
@Service
@Transactional
class CatalogModelManagerImpl implements CatalogModelManager {

  private final CatalogModelRepository repository;
  private final AiProviderRepository providerRepository;

  CatalogModelManagerImpl(
      CatalogModelRepository repository, AiProviderRepository providerRepository) {
    this.repository = Objects.requireNonNull(repository, "CatalogModelRepository cannot be null");
    this.providerRepository =
        Objects.requireNonNull(providerRepository, "AiProviderRepository cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public List<CatalogModelDto> getAllModels() {
    // Step routing must see live runs_on/status edits, even when an admin edits the rows directly.
    return repository.findAll().stream().map(CatalogModelManagerImpl::toDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "catalogModels", key = "#category")
  public List<CatalogModelDto> getModelsByCategory(String category) {
    Objects.requireNonNull(category, "Category cannot be null");
    return repository.findByCategory(category).stream()
        .map(CatalogModelManagerImpl::toDto)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "catalogModelDefault", key = "#category")
  public Optional<CatalogModelDto> getDefaultModelByCategory(String category) {
    Objects.requireNonNull(category, "Category cannot be null");
    return repository.findByCategoryAndIsDefaultTrue(category).map(CatalogModelManagerImpl::toDto);
  }

  @Override
  @CacheEvict(
      value = {"catalogModels", "catalogModelDefault"},
      allEntries = true)
  public CatalogModelDto saveModel(CatalogModelDto dto) {
    Objects.requireNonNull(dto, "CatalogModelDto cannot be null");

    // If the new/updated model is marked as default, unset other defaults in the same category
    if (Boolean.TRUE.equals(dto.isDefault())) {
      repository
          .findByCategory(dto.category())
          .forEach(
              entity -> {
                if (Boolean.TRUE.equals(entity.getIsDefault())
                    && !Objects.equals(entity.getId(), dto.id())) {
                  entity.setIsDefault(false);
                  repository.save(entity);
                }
              });
    }

    CatalogModelEntity entity = toEntity(dto);
    // The DTO does not carry a voice's locale (ADR-076: it is read through SpeechVoiceManager), so
    // an edit made through this port keeps the one the row declares instead of erasing it.
    if (dto.id() != null) {
      repository
          .findById(dto.id())
          .ifPresent(
              stored -> {
                entity.setLocale(stored.getLocale());
                entity.setLocaleDefault(stored.getLocaleDefault());
                // Same for the facts a picker shows (where it runs, status, measured time, price):
                // an edit through the narrow DTO must not blank what the catalogue declares.
                if (dto.runsOn() == null) {
                  entity.setRunsOn(stored.getRunsOn());
                }
                if (dto.typicalSeconds() == null) {
                  entity.setTypicalSeconds(stored.getTypicalSeconds());
                }
                if (dto.priceCents() == null) {
                  entity.setPriceCents(stored.getPriceCents());
                }
              });
    }
    CatalogModelEntity saved = repository.save(entity);
    return toDto(saved);
  }

  @Override
  @CacheEvict(
      value = {"catalogModels", "catalogModelDefault"},
      allEntries = true)
  public void deleteModel(Integer id) {
    Objects.requireNonNull(id, "ID cannot be null");
    repository.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> getAllProviders() {
    return providerRepository.findAll().stream().map(AiProviderEntity::getName).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public String getProviderBaseUrl(String providerName) {
    return providerRepository
        .findByName(providerName)
        .map(AiProviderEntity::getBaseUrl)
        .orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public String getProviderApiKey(String providerName) {
    return providerRepository
        .findByName(providerName)
        .map(AiProviderEntity::getApiKey)
        .orElse(null);
  }

  private static CatalogModelDto toDto(CatalogModelEntity entity) {
    if (entity == null) {
      return null;
    }
    return new CatalogModelDto(
        entity.getId(),
        entity.getModelName(),
        entity.getModelLabel(),
        entity.getCategory(),
        entity.getOptions(),
        entity.getIsDefault(),
        entity.getProviderName(),
        entity.getMaxSteps(),
        entity.getRecommendedFps(),
        entity.getSupportedHardware(),
        entity.getDescription(),
        entity.getRunsOn(),
        entity.getStatus(),
        entity.getTypicalSeconds(),
        entity.getPriceCents());
  }

  static CatalogModelEntity toEntity(CatalogModelDto dto) {
    if (dto == null) {
      return null;
    }
    CatalogModelEntity entity = new CatalogModelEntity();
    entity.setId(dto.id());
    entity.setModelName(dto.modelName());
    entity.setModelLabel(dto.modelLabel());
    entity.setCategory(dto.category());
    entity.setOptions(dto.options());
    entity.setIsDefault(dto.isDefault());
    entity.setProviderName(dto.providerName());
    entity.setMaxSteps(dto.maxSteps());
    entity.setRecommendedFps(dto.recommendedFps());
    entity.setSupportedHardware(dto.supportedHardware());
    entity.setDescription(dto.description());
    entity.setRunsOn(dto.runsOn());
    entity.setStatus(dto.status());
    entity.setTypicalSeconds(dto.typicalSeconds());
    entity.setPriceCents(dto.priceCents());
    return entity;
  }
}
