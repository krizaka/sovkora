package com.krizaka.sovkora.adapter.springai.memory;

import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatMemoryStore;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolAnswerCodec;
import com.krizaka.sovkora.persistence.domain.model.ChatMessageDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.ChatMemoryPersistenceProvider;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * Adapter binding the core {@link ChatMemoryStore} port to the persistence module's {@link
 * ChatMemoryPersistenceProvider} (Postgres). Mirrors DefaultChatSessionService.
 *
 * <p>Conversation memory is best-effort enrichment: a persistence failure (e.g. the table not yet
 * provisioned, or a transient DB error) must never break the chat response. Each operation
 * therefore degrades gracefully — reads return no history, writes are skipped — and logs a warning.
 */
public class PersistedChatMemoryStore implements ChatMemoryStore {

  private static final Logger logger = LoggerFactory.getLogger(PersistedChatMemoryStore.class);

  private final ChatMemoryPersistenceProvider provider;
  private final ToolAnswerCodec toolAnswers;

  public PersistedChatMemoryStore(
      ChatMemoryPersistenceProvider provider, ToolAnswerCodec toolAnswers) {
    this.provider =
        Objects.requireNonNull(provider, "ChatMemoryPersistenceProvider cannot be null");
    this.toolAnswers = Objects.requireNonNull(toolAnswers, "ToolAnswerCodec cannot be null");
  }

  @Override
  public List<EngineMessage> findRecent(String conversationId, int limit) {
    try {
      return provider.findRecent(conversationId, limit).stream().map(this::toMessage).toList();
    } catch (DataAccessException e) {
      logger.warn(
          "Conversation memory unavailable (read) for {} — continuing without history",
          conversationId,
          e);
      return List.of();
    }
  }

  @Override
  public void append(String conversationId, List<EngineMessage> messages) {
    try {
      provider.appendAll(
          messages.stream()
              .map(m -> new ChatMessageDto(conversationId, roleOf(m), contentOf(m), null))
              .toList());
    } catch (DataAccessException e) {
      logger.warn(
          "Conversation memory unavailable (write) for {} — turn not persisted", conversationId, e);
    }
  }

  @Override
  public void clear(String conversationId) {
    try {
      provider.deleteByConversationId(conversationId);
    } catch (DataAccessException e) {
      logger.warn("Conversation memory clear failed for {}", conversationId, e);
    }
  }

  private EngineMessage toMessage(ChatMessageDto dto) {
    return switch (dto.role().toLowerCase(Locale.ROOT)) {
      case "assistant" -> EngineMessage.assistant(dto.content());
      case "system" -> EngineMessage.system(dto.content());
      case "tool" -> toolAnswers.decode(dto.content());
      default -> EngineMessage.user(dto.content());
    };
  }

  /** The role as stored: the message's own, so a tool answer stays a tool answer. */
  private static String roleOf(EngineMessage message) {
    return message.role().name().toLowerCase(Locale.ROOT);
  }

  private String contentOf(EngineMessage message) {
    return toolAnswers.encode(message).orElseGet(message::text);
  }
}
