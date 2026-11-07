package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import java.util.List;

/**
 * Outbound port for durable conversation memory — the chronological transcript of a single
 * conversation. Implemented by an application-layer adapter over the persistence module. Used by
 * {@link MemoryResolver} so multi-turn context survives application restarts (the previous
 * in-memory store was lost on every restart).
 */
public interface ChatMemoryStore {

  /** Last {@code limit} messages of a conversation, chronological (oldest-first). */
  List<EngineMessage> findRecent(String conversationId, int limit);

  /** Appends messages to a conversation's transcript in order. */
  void append(String conversationId, List<EngineMessage> messages);

  /** Removes a conversation's whole transcript. */
  void clear(String conversationId);
}
