package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.ChatSessionEntity;
import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * Testcontainers integration test for {@link ChatSessionRepository}, exercising a real persist→read
 * round-trip against the singleton PostgreSQL container.
 *
 * <p>Extends {@link AbstractContainerIntegrationTest} — the single source of container wiring
 * (PostgreSQL+pgvector / Redis / RabbitMQ via {@code @DynamicPropertySource}). Every
 * container-based integration test in the codebase derives from that base; none configures
 * containers itself.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ChatSessionRepository — Testcontainers integration (singleton PostgreSQL)")
class ChatSessionRepositoryIT extends AbstractContainerIntegrationTest {

  @Autowired private ChatSessionRepository repository;

  @Test
  @DisplayName("persists a chat session and reads it back from the container database")
  void persistsAndReadsBackAgainstContainerPostgres() {
    String actorId = "actor-" + UUID.randomUUID();
    ChatSessionEntity entity = new ChatSessionEntity();
    entity.setId("chat-" + UUID.randomUUID());
    entity.setUserId(actorId);
    entity.setTitle("Integration round-trip");
    entity.setUpdatedAt(Instant.now());

    repository.save(entity);

    List<ChatSessionEntity> found = repository.findAllByUserIdOrderByUpdatedAtDesc(actorId);
    assertEquals(1, found.size());
    assertEquals(entity.getId(), found.getFirst().getId());
    assertEquals("Integration round-trip", found.getFirst().getTitle());
  }
}
