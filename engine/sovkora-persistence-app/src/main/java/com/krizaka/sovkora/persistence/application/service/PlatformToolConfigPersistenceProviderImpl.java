package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.model.PlatformToolConfigDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.PlatformToolConfigPersistenceProvider;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PlatformToolConfigEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.PlatformToolConfigRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Package-private implementation of PlatformToolConfigPersistenceProvider. */
@Service
class PlatformToolConfigPersistenceProviderImpl implements PlatformToolConfigPersistenceProvider {

  private final PlatformToolConfigRepository repository;

  PlatformToolConfigPersistenceProviderImpl(PlatformToolConfigRepository repository) {
    this.repository =
        Objects.requireNonNull(repository, "PlatformToolConfigRepository cannot be null");
  }

  @Override
  public Optional<PlatformToolConfigDto> findByToolId(String toolId) {
    Objects.requireNonNull(toolId, "toolId cannot be null");
    return repository.findByToolId(toolId).map(PlatformToolConfigPersistenceProviderImpl::toDto);
  }

  @Override
  public PlatformToolConfigDto save(PlatformToolConfigDto configDto) {
    Objects.requireNonNull(configDto, "PlatformToolConfigDto cannot be null");
    PlatformToolConfigEntity entity = toEntity(configDto);
    PlatformToolConfigEntity saved = repository.save(entity);
    return toDto(saved);
  }

  private static PlatformToolConfigDto toDto(PlatformToolConfigEntity entity) {
    if (entity == null) {
      return null;
    }
    return new PlatformToolConfigDto(
        entity.getId(),
        entity.getToolId(),
        entity.getCacheEnabled(),
        entity.getCacheTtlSeconds(),
        entity.getRagEnabled(),
        entity.getChunkerType(),
        entity.getSourceTable(),
        entity.getCreatedAt());
  }

  static PlatformToolConfigEntity toEntity(PlatformToolConfigDto dto) {
    if (dto == null) {
      return null;
    }
    PlatformToolConfigEntity entity = new PlatformToolConfigEntity();
    entity.setId(dto.id());
    entity.setToolId(dto.toolId());
    entity.setCacheEnabled(dto.cacheEnabled());
    entity.setCacheTtlSeconds(dto.cacheTtlSeconds());
    entity.setRagEnabled(dto.ragEnabled());
    entity.setChunkerType(dto.chunkerType());
    entity.setSourceTable(dto.sourceTable());
    entity.setCreatedAt(dto.createdAt());
    return entity;
  }
}
