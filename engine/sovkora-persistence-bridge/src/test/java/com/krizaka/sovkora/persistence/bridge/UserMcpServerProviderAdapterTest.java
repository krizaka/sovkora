package com.krizaka.sovkora.persistence.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.ports.outbound.UserMcpServerProvider;
import com.krizaka.sovkora.persistence.domain.model.UserMcpServerDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.UserMcpServerPersistenceProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserMcpServerProviderAdapterTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  private final UserMcpServerPersistenceProvider persistenceProvider =
      mock(UserMcpServerPersistenceProvider.class);
  private final UserMcpServerProviderAdapter provider =
      new UserMcpServerProviderAdapter(persistenceProvider);

  @Test
  void getActiveUserMcpServers_mapsFromPersistence() {
    var dto =
        new UserMcpServerDto(
            1, "user-1", "My MCP", "http://localhost", "token", true, Instant.now(FIXED_CLOCK));
    when(persistenceProvider.findByUserIdAndEnabledTrue("user-1")).thenReturn(List.of(dto));
    List<UserMcpServerProvider.UserMcpServer> result = provider.getActiveUserMcpServers("user-1");
    assertEquals(1, result.size());
    assertEquals("My MCP", result.getFirst().label());
    assertEquals("http://localhost", result.getFirst().url());
  }

  @Test
  void getActiveUserMcpServers_nullUserId_returnsEmpty() {
    List<UserMcpServerProvider.UserMcpServer> result = provider.getActiveUserMcpServers(null);
    assertTrue(result.isEmpty());
    verifyNoInteractions(persistenceProvider);
  }

  @Test
  void constructor_nullPersistenceProvider_throws() {
    assertThrows(NullPointerException.class, () -> new UserMcpServerProviderAdapter(null));
  }
}
