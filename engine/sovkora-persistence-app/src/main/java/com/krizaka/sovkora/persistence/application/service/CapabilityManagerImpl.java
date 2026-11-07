package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.jobs.domain.model.CapabilityDeclaration;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CapabilityManager;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.CapabilityEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.CapabilityRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link CapabilityManager} backed by the capability JPA repository. */
@Service
@Transactional
class CapabilityManagerImpl implements CapabilityManager {

  private final CapabilityRepository repository;

  CapabilityManagerImpl(CapabilityRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CapabilityDeclaration> findAll() {
    return repository.findAll().stream().map(CapabilityManagerImpl::toDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CapabilityDeclaration> findByFeatureKey(String featureKey) {
    return repository.findById(featureKey).map(CapabilityManagerImpl::toDto);
  }

  @Override
  public CapabilityDeclaration save(CapabilityDeclaration capability) {
    return toDto(repository.save(toEntity(capability)));
  }

  @Override
  public boolean delete(String featureKey) {
    if (featureKey == null || !repository.existsById(featureKey)) {
      return false;
    }
    repository.deleteById(featureKey);
    return true;
  }

  private static CapabilityDeclaration toDto(CapabilityEntity entity) {
    return new CapabilityDeclaration(
        entity.getFeatureKey(),
        entity.getHandlerKey(),
        entity.getRoutingKey(),
        entity.getBillableUnit(),
        entity.getBillableCapability(),
        entity.getLatencyClass(),
        entity.getInputSchema(),
        entity.getOutputSchema(),
        Boolean.TRUE.equals(entity.getIsEnabled()));
  }

  static CapabilityEntity toEntity(CapabilityDeclaration dto) {
    CapabilityEntity entity = new CapabilityEntity();
    entity.setFeatureKey(dto.featureKey());
    entity.setHandlerKey(dto.handlerKey());
    entity.setRoutingKey(dto.routingKey());
    entity.setBillableUnit(dto.billableUnit());
    entity.setLatencyClass(dto.latencyClass());
    // `{}` and not null: the column is NOT NULL because an undeclared contract must be
    // representable and must match nothing, never absent (ADR-069).
    entity.setInputSchema(dto.inputSchema() == null ? "{}" : dto.inputSchema());
    entity.setOutputSchema(dto.outputSchema() == null ? "{}" : dto.outputSchema());
    entity.setBillableCapability(dto.billableCapability());
    entity.setIsEnabled(dto.enabled());
    return entity;
  }
}
