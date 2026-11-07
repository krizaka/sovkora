package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.ChatSessionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA Repository for performing database operations on {@link ChatSessionEntity}. */
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {

  /**
   * Finds all chat sessions belonging to a specific user ordered by updatedAt descending.
   *
   * @param userId The user ID.
   * @return A list of ChatSessionEntity.
   */
  List<ChatSessionEntity> findAllByUserIdOrderByUpdatedAtDesc(String userId);

  /**
   * Deletes all chat sessions belonging to a specific user.
   *
   * @param userId The user ID.
   */
  void deleteAllByUserId(String userId);
}
