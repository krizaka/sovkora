package com.krizaka.sovkora.persistence.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.persistence.domain.model.ChatSessionDto;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.ChatSessionEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.ChatSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatSessionPersistenceProviderImplTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Mock private ChatSessionRepository repository;

  @InjectMocks private ChatSessionPersistenceProviderImpl provider;

  @Test
  @DisplayName("save persists and maps ChatSession")
  void saveSession() {
    Instant now = Instant.now(FIXED_CLOCK);
    ChatSessionDto dto = new ChatSessionDto("sess-1", "user-1", "Title", now);
    ChatSessionEntity entity = ChatSessionPersistenceProviderImpl.toEntity(dto);

    when(repository.save(any(ChatSessionEntity.class))).thenReturn(entity);

    ChatSessionDto saved = provider.save(dto);

    assertNotNull(saved);
    assertEquals("sess-1", saved.id());
  }

  @Test
  @DisplayName("findById retrieves and maps ChatSession by ID")
  void findById() {
    Instant now = Instant.now(FIXED_CLOCK);
    ChatSessionEntity entity = new ChatSessionEntity();
    entity.setId("sess-1");
    entity.setUserId("user-1");
    entity.setTitle("Title");
    entity.setUpdatedAt(now);

    when(repository.findById("sess-1")).thenReturn(Optional.of(entity));

    Optional<ChatSessionDto> result = provider.findById("sess-1");

    assertTrue(result.isPresent());
    assertEquals("sess-1", result.get().id());
  }

  @Test
  @DisplayName("findAllByUserId retrieves and maps all user sessions ordered by updatedAt")
  void findAllByUserId() {
    Instant now = Instant.now(FIXED_CLOCK);
    ChatSessionEntity entity = new ChatSessionEntity();
    entity.setId("sess-1");
    entity.setUserId("user-1");
    entity.setTitle("Title");
    entity.setUpdatedAt(now);

    when(repository.findAllByUserIdOrderByUpdatedAtDesc("user-1")).thenReturn(List.of(entity));

    List<ChatSessionDto> result = provider.findAllByUserId("user-1");

    assertEquals(1, result.size());
    assertEquals("user-1", result.getFirst().userId());
  }

  @Test
  @DisplayName("deleteById calls repository delete")
  void deleteById() {
    doNothing().when(repository).deleteById("sess-1");
    provider.deleteById("sess-1");
    verify(repository).deleteById("sess-1");
  }

  @Test
  @DisplayName("purgeByUserId calls repository deleteAllByUserId")
  void purgeByUserId() {
    doNothing().when(repository).deleteAllByUserId("user-1");
    provider.purgeByUserId("user-1");
    verify(repository).deleteAllByUserId("user-1");
  }
}
