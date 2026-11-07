package com.krizaka.sovkora.engine.persistence.api.domain.ports.inbound;

import com.krizaka.sovkora.persistence.api.domain.model.ChatMessageDto;
import java.util.List;

/**
 * Inbound port contract for durable conversation memory — the chronological turn-by-turn transcript
 * of a single conversation, used by the Memory interceptor to inject multi-turn context. Short-term
 * conversational recall is a relational, recency-ordered access pattern, hence Postgres (not the
 * vector store, which serves long-term semantic RAG).
 */
public interface ChatMemoryPersistenceProvider {

  /**
   * Returns the last {@code limit} messages of a conversation in chronological (oldest-first)
   * order.
   */
  List<ChatMessageDto> findRecent(String conversationId, int limit);

  /** Appends messages to a conversation's transcript, preserving insertion order. */
  void appendAll(List<ChatMessageDto> messages);

  /** Removes every message of a conversation (thread closed / eviction). */
  void deleteByConversationId(String conversationId);
}
