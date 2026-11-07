package com.krizaka.sovkora.core.application.service;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.model.chat.ChatSessionInfo;
import com.krizaka.sovkora.persistence.domain.model.ChatSessionDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.ChatSessionPersistenceProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultChatSessionServiceTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Mock private ChatSessionPersistenceProvider provider;

  private DefaultChatSessionService service;

  @BeforeEach
  void setUp() {
    service = new DefaultChatSessionService(provider);
  }

  @Test
  void constructorShouldThrowOnNullProvider() {
    assertThatThrownBy(() -> new DefaultChatSessionService(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("ChatSessionPersistenceProvider cannot be null");
  }

  @Test
  void shouldSaveSession() {
    Instant now = Instant.now(FIXED_CLOCK);
    ChatSessionInfo info = new ChatSessionInfo(S1, U1, TITLE, now);
    ChatSessionDto dto = new ChatSessionDto(S1, U1, TITLE, now);
    when(provider.save(dto)).thenReturn(dto);

    ChatSessionInfo result = service.save(info);

    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(S1);
    verify(provider).save(dto);
  }

  @Test
  void shouldGetSession() {
    Instant now = Instant.now(FIXED_CLOCK);
    ChatSessionDto dto = new ChatSessionDto(S1, U1, TITLE, now);
    when(provider.findById(S1)).thenReturn(Optional.of(dto));

    Optional<ChatSessionInfo> result = service.getSession(S1);

    assertThat(result).isPresent();
    assertThat(result.get().title()).isEqualTo(TITLE);
    verify(provider).findById(S1);
  }

  @Test
  void shouldGetSessionsByUserId() {
    Instant now = Instant.now(FIXED_CLOCK);
    ChatSessionDto dto = new ChatSessionDto(S1, U1, TITLE, now);
    when(provider.findAllByUserId(U1)).thenReturn(List.of(dto));

    List<ChatSessionInfo> result = service.getSessionsByUserId(U1);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().userId()).isEqualTo(U1);
    verify(provider).findAllByUserId(U1);
  }

  @Test
  void shouldDeleteSession() {
    service.deleteSession(S1);
    verify(provider).deleteById(S1);
  }

  @Test
  void shouldPurgeSessionsByUserId() {
    service.purgeSessionsByUserId(U1);
    verify(provider).purgeByUserId(U1);
  }
}
