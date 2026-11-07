package com.krizaka.sovkora.persistence.application.service;

import com.krizaka.sovkora.persistence.domain.model.ChatMessageDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.ChatMemoryPersistenceProvider;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.ChatMessageEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.ChatMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ChatMemoryPersistenceProviderImpl implements ChatMemoryPersistenceProvider {

  private final ChatMessageRepository repository;

  ChatMemoryPersistenceProviderImpl(ChatMessageRepository repository) {
    this.repository = Objects.requireNonNull(repository, "ChatMessageRepository cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChatMessageDto> findRecent(String conversationId, int limit) {
    List<ChatMessageEntity> recentDesc =
        repository.findByConversationIdOrderByIdDesc(conversationId, PageRequest.of(0, limit));
    // Stored newest-first for the cap; flip back to chronological for prompt injection.
    return recentDesc.reversed().stream().map(ChatMemoryPersistenceProviderImpl::toDto).toList();
  }

  @Override
  public void appendAll(List<ChatMessageDto> messages) {
    repository.saveAll(messages.stream().map(ChatMemoryPersistenceProviderImpl::toEntity).toList());
  }

  @Override
  public void deleteByConversationId(String conversationId) {
    repository.deleteByConversationId(conversationId);
  }

  static ChatMessageEntity toEntity(ChatMessageDto dto) {
    if (dto == null) {
      return null;
    }
    ChatMessageEntity entity = new ChatMessageEntity();
    entity.setConversationId(dto.conversationId());
    entity.setRole(dto.role());
    entity.setContent(dto.content());
    entity.setCreatedAt(dto.createdAt() != null ? dto.createdAt() : Instant.now());
    return entity;
  }

  private static ChatMessageDto toDto(ChatMessageEntity entity) {
    if (entity == null) {
      return null;
    }
    return new ChatMessageDto(
        entity.getConversationId(), entity.getRole(), entity.getContent(), entity.getCreatedAt());
  }
}
