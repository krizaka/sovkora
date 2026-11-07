package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.model.PlatformMcpServerDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.PlatformMcpServerPersistenceProvider;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PlatformMcpServerEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.PlatformMcpServerRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Package-private implementation of PlatformMcpServerPersistenceProvider. */
@Service
class PlatformMcpServerPersistenceProviderImpl implements PlatformMcpServerPersistenceProvider {

  private final PlatformMcpServerRepository repository;
  private final McpTokenSealer tokens;

  PlatformMcpServerPersistenceProviderImpl(
      PlatformMcpServerRepository repository, McpTokenSealer tokens) {
    this.repository =
        Objects.requireNonNull(repository, "PlatformMcpServerRepository cannot be null");
    this.tokens = Objects.requireNonNull(tokens, "McpTokenSealer cannot be null");
  }

  @Override
  public List<PlatformMcpServerDto> findByEnabledTrue() {
    return repository.findByEnabledTrue().stream().map(this::toDto).toList();
  }

  @Override
  public List<PlatformMcpServerDto> findAll() {
    return repository.findAll().stream().map(this::toDto).toList();
  }

  @Override
  public Optional<PlatformMcpServerDto> findById(Integer id) {
    Objects.requireNonNull(id, "id cannot be null");
    return repository.findById(id).map(this::toDto);
  }

  @Override
  public PlatformMcpServerDto save(PlatformMcpServerDto serverDto) {
    Objects.requireNonNull(serverDto, "PlatformMcpServerDto cannot be null");
    PlatformMcpServerEntity entity = toEntity(serverDto);
    PlatformMcpServerEntity saved = repository.save(entity);
    return toDto(saved);
  }

  @Override
  public void deleteById(Integer id) {
    Objects.requireNonNull(id, "id cannot be null");
    repository.deleteById(id);
  }

  private PlatformMcpServerDto toDto(PlatformMcpServerEntity entity) {
    if (entity == null) {
      return null;
    }
    return new PlatformMcpServerDto(
        entity.getId(),
        entity.getLabel(),
        entity.getTransportType(),
        entity.getUrl(),
        entity.getCommand(),
        entity.getArgs(),
        tokens.open(null, entity.getAuthToken()),
        entity.getEnabled(),
        entity.getCreatedAt());
  }

  PlatformMcpServerEntity toEntity(PlatformMcpServerDto dto) {
    if (dto == null) {
      return null;
    }
    PlatformMcpServerEntity entity = new PlatformMcpServerEntity();
    entity.setId(dto.id());
    entity.setLabel(dto.label());
    entity.setTransportType(dto.transportType());
    entity.setUrl(dto.url());
    entity.setCommand(dto.command());
    entity.setArgs(dto.args());
    // Sealed before it reaches the table (sovkora-engine#58).
    entity.setAuthToken(tokens.seal(null, dto.authToken()));
    entity.setEnabled(dto.enabled());
    entity.setCreatedAt(dto.createdAt());
    return entity;
  }
}
