package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.model.ValidationPipelineConfigDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.ValidationPipelineManager;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.ValidationPipelineConfigEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.ValidationPipelineConfigJpaRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link ValidationPipelineManager} backed by the validation-pipeline JPA repository. */
@Service
@Transactional
class ValidationPipelineManagerImpl implements ValidationPipelineManager {

  private final ValidationPipelineConfigJpaRepository repository;

  ValidationPipelineManagerImpl(ValidationPipelineConfigJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ValidationPipelineConfigDto> findAllOrderedByExecution() {
    return repository.findAllByOrderByExecutionOrderAsc().stream()
        .map(ValidationPipelineManagerImpl::toDto)
        .toList();
  }

  @Override
  public ValidationPipelineConfigDto save(ValidationPipelineConfigDto config) {
    ValidationPipelineConfigEntity entity =
        repository
            .findByStepType(config.stepType())
            .map(
                existing -> {
                  existing.setIsEnabled(config.enabled());
                  existing.setExecutionOrder(config.executionOrder());
                  existing.setConfigurationPayload(config.configurationPayload());
                  return existing;
                })
            .orElseGet(() -> toEntity(config));
    return toDto(repository.save(entity));
  }

  @Override
  public List<ValidationPipelineConfigDto> saveAll(List<ValidationPipelineConfigDto> configs) {
    return configs.stream().map(this::save).toList();
  }

  private static ValidationPipelineConfigDto toDto(ValidationPipelineConfigEntity entity) {
    Map<String, Object> payload =
        entity.getConfigurationPayload() != null ? entity.getConfigurationPayload() : Map.of();
    return new ValidationPipelineConfigDto(
        entity.getId(),
        entity.getStepType(),
        Boolean.TRUE.equals(entity.getIsEnabled()),
        entity.getExecutionOrder(),
        payload);
  }

  static ValidationPipelineConfigEntity toEntity(ValidationPipelineConfigDto dto) {
    var entity = new ValidationPipelineConfigEntity();
    entity.setId(dto.id());
    entity.setStepType(dto.stepType());
    entity.setIsEnabled(dto.enabled());
    entity.setExecutionOrder(dto.executionOrder());
    entity.setConfigurationPayload(dto.configurationPayload());
    return entity;
  }
}
