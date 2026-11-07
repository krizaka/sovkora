package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.model.ChatSessionDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.ChatSessionPersistenceProvider;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.ChatSessionEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.ChatSessionRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ChatSessionPersistenceProviderImpl implements ChatSessionPersistenceProvider {

  private final ChatSessionRepository repository;

  ChatSessionPersistenceProviderImpl(ChatSessionRepository repository) {
    this.repository = Objects.requireNonNull(repository, "ChatSessionRepository cannot be null");
  }

  @Override
  public ChatSessionDto save(ChatSessionDto session) {
    var entity = toEntity(session);
    var saved = repository.save(entity);
    return toDto(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ChatSessionDto> findById(String id) {
    return repository.findById(id).map(ChatSessionPersistenceProviderImpl::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChatSessionDto> findAllByUserId(String userId) {
    return repository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
        .map(ChatSessionPersistenceProviderImpl::toDto)
        .toList();
  }

  @Override
  public void deleteById(String id) {
    repository.deleteById(id);
  }

  @Override
  public void purgeByUserId(String userId) {
    repository.deleteAllByUserId(userId);
  }

  static ChatSessionEntity toEntity(ChatSessionDto dto) {
    if (dto == null) {
      return null;
    }
    ChatSessionEntity entity = new ChatSessionEntity();
    entity.setId(dto.id());
    entity.setUserId(dto.userId());
    entity.setTitle(dto.title());
    entity.setUpdatedAt(dto.updatedAt());
    return entity;
  }

  private static ChatSessionDto toDto(ChatSessionEntity entity) {
    if (entity == null) {
      return null;
    }
    return new ChatSessionDto(
        entity.getId(), entity.getUserId(), entity.getTitle(), entity.getUpdatedAt());
  }
}
