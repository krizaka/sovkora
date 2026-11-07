package com.krizaka.sovkora.persistence.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.ports.outbound.PlatformToolConfigProvider;
import com.krizaka.sovkora.persistence.domain.model.PlatformToolConfigDto;
import com.krizaka.sovkora.persistence.domain.ports.inbound.PlatformToolConfigPersistenceProvider;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformToolConfigProviderAdapterTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  private final PlatformToolConfigPersistenceProvider persistenceProvider =
      mock(PlatformToolConfigPersistenceProvider.class);
  private final PlatformToolConfigProviderAdapter provider =
      new PlatformToolConfigProviderAdapter(persistenceProvider);

  @Test
  void getToolConfig_delegatesToPersistence() {
    var dto =
        new PlatformToolConfigDto(
            1, "tool-1", true, 600, false, "FIXED", "table", Instant.now(FIXED_CLOCK));
    when(persistenceProvider.findByToolId("tool-1")).thenReturn(Optional.of(dto));
    Optional<PlatformToolConfigProvider.PlatformToolConfig> result =
        provider.getToolConfig("tool-1");
    assertTrue(result.isPresent());
    assertEquals("tool-1", result.get().toolId());
    assertTrue(result.get().cacheEnabled());
    assertEquals(600, result.get().cacheTtlSeconds());
  }

  @Test
  void getToolConfig_notFound_returnsEmpty() {
    when(persistenceProvider.findByToolId("unknown")).thenReturn(Optional.empty());
    assertTrue(provider.getToolConfig("unknown").isEmpty());
  }

  @Test
  void getToolConfig_nullToolId_returnsEmpty() {
    assertTrue(provider.getToolConfig(null).isEmpty());
    verifyNoInteractions(persistenceProvider);
  }

  @Test
  void constructor_nullPersistenceProvider_throws() {
    assertThrows(NullPointerException.class, () -> new PlatformToolConfigProviderAdapter(null));
  }
}
