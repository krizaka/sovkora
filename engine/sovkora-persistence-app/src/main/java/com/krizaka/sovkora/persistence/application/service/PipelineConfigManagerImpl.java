package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.model.InterceptorConfigDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.PipelineConfigManager;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PipelineInterceptorConfigEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.PipelineInterceptorConfigRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link PipelineConfigManager} backed by the pipeline-interceptor JPA repository. */
@Service
@Transactional
class PipelineConfigManagerImpl implements PipelineConfigManager {

  private final PipelineInterceptorConfigRepository repository;

  PipelineConfigManagerImpl(PipelineInterceptorConfigRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<InterceptorConfigDto> findAllOrdered() {
    return repository.findAllByOrderByExecutionOrderAscIdAsc().stream()
        .map(PipelineConfigManagerImpl::toDto)
        .toList();
  }

  @Override
  public InterceptorConfigDto save(InterceptorConfigDto config) {
    PipelineInterceptorConfigEntity entity =
        repository
            .findByInterceptorKey(config.interceptorKey())
            .map(
                existing -> {
                  existing.setDisplayLabel(config.displayLabel());
                  existing.setExecutionOrder(config.executionOrder());
                  existing.setIsEnabled(config.enabled());
                  existing.setDescription(config.description());
                  return existing;
                })
            .orElseGet(() -> toEntity(config));
    return toDto(repository.save(entity));
  }

  @Override
  public List<InterceptorConfigDto> saveAll(List<InterceptorConfigDto> configs) {
    return configs.stream().map(this::save).toList();
  }

  @Override
  public void resetToDefaults(List<InterceptorConfigDto> defaults) {
    // deleteAllInBatch() issues an immediate bulk DELETE so the rows are gone before the inserts;
    // a queued deleteAll() would flush INSERTs first and collide with the unique interceptor_key.
    repository.deleteAllInBatch();
    repository.saveAll(defaults.stream().map(PipelineConfigManagerImpl::toEntity).toList());
  }

  private static InterceptorConfigDto toDto(PipelineInterceptorConfigEntity entity) {
    return new InterceptorConfigDto(
        entity.getInterceptorKey(),
        entity.getDisplayLabel(),
        entity.getExecutionOrder(),
        Boolean.TRUE.equals(entity.getIsEnabled()),
        entity.getDescription());
  }

  static PipelineInterceptorConfigEntity toEntity(InterceptorConfigDto dto) {
    var entity = new PipelineInterceptorConfigEntity();
    entity.setInterceptorKey(dto.interceptorKey());
    entity.setDisplayLabel(dto.displayLabel());
    entity.setExecutionOrder(dto.executionOrder());
    entity.setIsEnabled(dto.enabled());
    entity.setDescription(dto.description());
    return entity;
  }
}
