package com.krizaka.sovkora.conversation.infrastructure.adapter.persistence;

import com.krizaka.sovkora.conversation.domain.port.ProtectedConversationStore;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * The index of regulated conversations over this service's own table ({@code
 * orazaka_protected_conversation}), and the erasure that goes through it.
 *
 * <p>Every erasure is one statement over the index row, the messages and the chat session row, so a
 * crash cannot leave messages nobody can find again. The clock is the database's ({@code now()}).
 *
 * <p>Every statement is a constant: the actor, the pack and the window are bound parameters, never
 * text of the query (krizaka-internal#4361).
 */
@Component
class JdbcProtectedConversationStoreAdapter implements ProtectedConversationStore {

  private static final String ERASE_OF =
      """
      WITH gone AS (
        DELETE FROM orazaka_protected_conversation WHERE actor_id = ? AND pack_key = ?
        RETURNING conversation_id),
      messages AS (
        DELETE FROM orazaka_chat_messages
        WHERE conversation_id IN (SELECT conversation_id FROM gone) RETURNING 1),
      sessions AS (
        DELETE FROM orazaka_chat_sessions
        WHERE user_id = ? AND id IN (SELECT conversation_id FROM gone) RETURNING 1)
      SELECT (SELECT count(*) FROM gone), (SELECT count(*) FROM messages)""";

  private static final String ERASE_OLDER_THAN =
      """
      WITH gone AS (
        DELETE FROM orazaka_protected_conversation
        WHERE last_seen_at < now() - make_interval(days => ?)
        RETURNING conversation_id),
      messages AS (
        DELETE FROM orazaka_chat_messages
        WHERE conversation_id IN (SELECT conversation_id FROM gone) RETURNING 1),
      sessions AS (
        DELETE FROM orazaka_chat_sessions
        WHERE id IN (SELECT conversation_id FROM gone) RETURNING 1)
      SELECT (SELECT count(*) FROM gone), (SELECT count(*) FROM messages)""";

  /** The two counts the erasure statements select: conversations, then messages. */
  private static final RowMapper<Erased> ERASED =
      (row, rowNum) -> new Erased(row.getInt(1), row.getInt(2));

  private final JdbcTemplate jdbcTemplate;

  JdbcProtectedConversationStoreAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
  }

  @Override
  public void record(String conversationId, String actorId, String installationId, String packKey) {
    jdbcTemplate.update(
        "INSERT INTO orazaka_protected_conversation"
            + " (conversation_id, actor_id, installation_id, pack_key) VALUES (?, ?, ?, ?)"
            + " ON CONFLICT (conversation_id) DO UPDATE SET last_seen_at = now()",
        conversationId,
        actorId,
        installationId,
        packKey);
  }

  @Override
  public Erased eraseOf(String actorId, String packKey) {
    return jdbcTemplate.queryForObject(ERASE_OF, ERASED, actorId, packKey, actorId);
  }

  @Override
  public Erased eraseOlderThan(int days) {
    return jdbcTemplate.queryForObject(ERASE_OLDER_THAN, ERASED, days);
  }
}
