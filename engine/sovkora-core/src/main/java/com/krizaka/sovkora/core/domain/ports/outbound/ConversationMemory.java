package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import java.util.List;

/**
 * The memory of one conversation, as {@link MemoryResolver} hands it out: the messages said so far,
 * oldest first, in the engine's own type (ADR-092 §4 rule 1).
 */
public interface ConversationMemory {

  /** Appends messages to the conversation, in order. */
  void add(String conversationId, List<EngineMessage> messages);

  /** The messages kept for the conversation, oldest first. */
  List<EngineMessage> get(String conversationId);

  /** Forgets the conversation. */
  void clear(String conversationId);
}
