package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.model.UserMcpServerDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.UserMcpServerPersistenceProvider;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.UserMcpServerEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.UserMcpServerRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Package-private implementation of UserMcpServerPersistenceProvider. */
@Service
class UserMcpServerPersistenceProviderImpl implements UserMcpServerPersistenceProvider {

  private final UserMcpServerRepository repository;
  private final McpTokenSealer tokens;

  UserMcpServerPersistenceProviderImpl(UserMcpServerRepository repository, McpTokenSealer tokens) {
    this.repository = Objects.requireNonNull(repository, "UserMcpServerRepository cannot be null");
    this.tokens = Objects.requireNonNull(tokens, "McpTokenSealer cannot be null");
  }

  @Override
  public List<UserMcpServerDto> findByUserIdAndEnabledTrue(String userId) {
    Objects.requireNonNull(userId, "userId cannot be null");
    return repository.findByUserIdAndEnabledTrue(userId).stream().map(this::toDto).toList();
  }

  @Override
  public List<UserMcpServerDto> findByUserId(String userId) {
    Objects.requireNonNull(userId, "userId cannot be null");
    return repository.findByUserId(userId).stream().map(this::toDto).toList();
  }

  @Override
  public Optional<UserMcpServerDto> findById(Integer id) {
    Objects.requireNonNull(id, "id cannot be null");
    return repository.findById(id).map(this::toDto);
  }

  @Override
  public UserMcpServerDto save(UserMcpServerDto serverDto) {
    Objects.requireNonNull(serverDto, "UserMcpServerDto cannot be null");
    UserMcpServerEntity entity = toEntity(serverDto);
    UserMcpServerEntity saved = repository.save(entity);
    return toDto(saved);
  }

  @Override
  public void deleteById(Integer id) {
    Objects.requireNonNull(id, "id cannot be null");
    repository.deleteById(id);
  }

  private UserMcpServerDto toDto(UserMcpServerEntity entity) {
    if (entity == null) {
      return null;
    }
    return new UserMcpServerDto(
        entity.getId(),
        entity.getUserId(),
        entity.getLabel(),
        entity.getUrl(),
        tokens.open(entity.getUserId(), entity.getAuthToken()),
        entity.getEnabled(),
        entity.getCreatedAt());
  }

  UserMcpServerEntity toEntity(UserMcpServerDto dto) {
    if (dto == null) {
      return null;
    }
    UserMcpServerEntity entity = new UserMcpServerEntity();
    entity.setId(dto.id());
    entity.setUserId(dto.userId());
    entity.setLabel(dto.label());
    entity.setUrl(dto.url());
    // Sealed before it reaches the table (sovkora-engine#58).
    entity.setAuthToken(tokens.seal(dto.userId(), dto.authToken()));
    entity.setEnabled(dto.enabled());
    entity.setCreatedAt(dto.createdAt());
    return entity;
  }
}
