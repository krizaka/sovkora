package com.krizaka.sovkora.persistence.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.krizaka.sovkora.persistence.domain.model.PlatformMcpServerDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.PlatformMcpServerPersistenceProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformMcpServerProviderAdapterTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  private final PlatformMcpServerPersistenceProvider persistenceProvider =
      mock(PlatformMcpServerPersistenceProvider.class);
  private final PlatformMcpServerProviderAdapter provider =
      new PlatformMcpServerProviderAdapter(persistenceProvider);

  @Test
  void getActivePlatformMcpServers_mapsFromPersistence() {
    var dto =
        new PlatformMcpServerDto(
            1, "GitHub", "STDIO", "url", "node", "--args", "token", true, Instant.now(FIXED_CLOCK));
    when(persistenceProvider.findByEnabledTrue()).thenReturn(List.of(dto));
    List<PlatformMcpServerProvider.PlatformMcpServer> result =
        provider.getActivePlatformMcpServers();
    assertEquals(1, result.size());
    assertEquals("GitHub", result.getFirst().label());
    assertEquals("STDIO", result.getFirst().transportType());
  }

  @Test
  void getActivePlatformMcpServers_emptyList() {
    when(persistenceProvider.findByEnabledTrue()).thenReturn(List.of());
    assertTrue(provider.getActivePlatformMcpServers().isEmpty());
  }

  @Test
  void constructor_nullPersistenceProvider_throws() {
    assertThrows(NullPointerException.class, () -> new PlatformMcpServerProviderAdapter(null));
  }
}
