package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.ChatMessageEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA Repository for the conversation transcript ({@link ChatMessageEntity}). */
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

  /**
   * Most recent messages first; the provider re-orders to chronological and caps via {@link
   * Pageable}.
   */
  List<ChatMessageEntity> findByConversationIdOrderByIdDesc(
      String conversationId, Pageable pageable);

  /** Deletes every message of a conversation. */
  void deleteByConversationId(String conversationId);
}
