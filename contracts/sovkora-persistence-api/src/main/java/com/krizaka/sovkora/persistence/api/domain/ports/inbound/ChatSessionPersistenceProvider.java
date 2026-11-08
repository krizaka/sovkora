package com.krizaka.sovkora.persistence.api.domain.ports.inbound;

import com.krizaka.sovkora.persistence.api.domain.model.ChatSessionDto;
import java.util.List;
import java.util.Optional;

/** Inbound port contract for managing chat sessions in the database. */
public interface ChatSessionPersistenceProvider {

  /** Saves a chat session (creation or update). */
  ChatSessionDto save(ChatSessionDto session);

  /** Finds a chat session by ID. */
  Optional<ChatSessionDto> findById(String id);

  /** Finds all chat sessions of a specific user. */
  List<ChatSessionDto> findAllByUserId(String userId);

  /** Deletes a chat session by ID. */
  void deleteById(String id);

  /** Purges all chat sessions of a user. */
  void purgeByUserId(String userId);
}
