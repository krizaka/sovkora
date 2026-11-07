package com.krizaka.sovkora.core.domain.ports.inbound;

import com.krizaka.sovkora.core.domain.model.chat.ChatSessionInfo;
import java.util.List;
import java.util.Optional;

/** Inbound port for managing user chat sessions/memory blocks in the core layer. */
public interface ChatSessionService {

  /** Saves a chat session (creates or updates). */
  ChatSessionInfo save(ChatSessionInfo session);

  /** Retrieves a chat session by ID. */
  Optional<ChatSessionInfo> getSession(String id);

  /** Retrieves all chat sessions owned by a specific user. */
  List<ChatSessionInfo> getSessionsByUserId(String userId);

  /** Deletes a chat session by ID. */
  void deleteSession(String id);

  /** Purges all chat sessions owned by a specific user. */
  void purgeSessionsByUserId(String userId);
}
