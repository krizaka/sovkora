package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.sovkora.core.domain.ports.outbound.ValidationPipelineRepository;
import com.krizaka.sovkora.persistence.domain.ports.inbound.ValidationPipelineManager;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Router-layer adapter implementing the core {@link ValidationPipelineRepository} outbound port by
 * delegating to the persistence {@link ValidationPipelineManager} inbound port — all DB access
 * stays in the persistence module; this adapter only maps the persistence DTO ↔ the core record.
 */
@Service
class ValidationPipelineRepositoryAdapter implements ValidationPipelineRepository {

  private final ValidationPipelineManager validationPipelineManager;

  ValidationPipelineRepositoryAdapter(ValidationPipelineManager validationPipelineManager) {
    this.validationPipelineManager =
        Objects.requireNonNull(
            validationPipelineManager, "ValidationPipelineManager cannot be null");
  }

  @Override
  public List<ValidationPipelineConfiguration> findAllOrderedByExecution() {
    return validationPipelineManager.findAllOrderedByExecution().stream()
        .map(ValidationPipelineConfigMapper::toRecord)
        .toList();
  }

  @Override
  public ValidationPipelineConfiguration save(ValidationPipelineConfiguration config) {
    return ValidationPipelineConfigMapper.toRecord(
        validationPipelineManager.save(ValidationPipelineConfigMapper.toDto(config)));
  }

  @Override
  public List<ValidationPipelineConfiguration> saveAll(
      List<ValidationPipelineConfiguration> configs) {
    return validationPipelineManager
        .saveAll(configs.stream().map(ValidationPipelineConfigMapper::toDto).toList())
        .stream()
        .map(ValidationPipelineConfigMapper::toRecord)
        .toList();
  }
}
