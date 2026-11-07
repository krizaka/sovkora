package com.krizaka.sovkora.persistence.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.persistence.domain.model.PlatformMcpServerDto;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PlatformMcpServerEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.PlatformMcpServerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformMcpServerPersistenceProviderImplTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Mock private PlatformMcpServerRepository repository;

  @org.junit.jupiter.api.io.TempDir java.nio.file.Path keyringDir;

  private McpTokenSealer tokens;
  private PlatformMcpServerPersistenceProviderImpl provider;

  @org.junit.jupiter.api.BeforeEach
  void setUp() throws Exception {
    java.nio.file.Path keyring = keyringDir.resolve("master.key");
    com.krizaka.sovkora.assets.infrastructure.adapter.FileMasterKeyProvider.addKey(keyring, "k1");
    var keys = new com.krizaka.sovkora.assets.infrastructure.adapter.FileMasterKeyProvider(keyring);
    org.springframework.beans.factory.ObjectProvider<
            com.krizaka.sovkora.assets.domain.port.MasterKeyProvider>
        provided = mock(org.springframework.beans.factory.ObjectProvider.class);
    lenient().when(provided.getIfAvailable()).thenReturn(keys);
    tokens = new McpTokenSealer(provided);
    provider = new PlatformMcpServerPersistenceProviderImpl(repository, tokens);
  }

  @Test
  @DisplayName("[#58] a token is stored sealed, never in clear, and reads back for the client")
  void theTokenIsStoredSealed() {
    PlatformMcpServerDto dto =
        new PlatformMcpServerDto(
            1,
            "MCP",
            "SSE",
            "https://mcp.example",
            null,
            null,
            "secret-token",
            true,
            Instant.now(FIXED_CLOCK));
    org.mockito.ArgumentCaptor<PlatformMcpServerEntity> stored =
        org.mockito.ArgumentCaptor.forClass(PlatformMcpServerEntity.class);
    when(repository.save(stored.capture())).thenAnswer(call -> call.getArgument(0));

    PlatformMcpServerDto saved = provider.save(dto);

    assertThat(stored.getValue().getAuthToken())
        .isNotNull()
        .doesNotContain("secret-token")
        .startsWith("v1.");
    assertThat(saved.authToken()).isEqualTo("secret-token");
  }

  @Test
  @DisplayName("[#58] a row written in clear before #58 still reads, until the migration seals it")
  void aLegacyClearRowStillReads() {
    PlatformMcpServerEntity legacy = new PlatformMcpServerEntity();
    legacy.setId(7);
    legacy.setLabel("legacy");
    legacy.setTransportType("SSE");
    legacy.setUrl("https://mcp.example");
    legacy.setAuthToken("legacy-token");
    when(repository.findById(7)).thenReturn(java.util.Optional.of(legacy));

    assertThat(provider.findById(7)).map(PlatformMcpServerDto::authToken).contains("legacy-token");
  }

  @Test
  @DisplayName("findByEnabledTrue retrieves enabled servers")
  void findByEnabledTrue() {
    PlatformMcpServerEntity entity = new PlatformMcpServerEntity();
    entity.setId(1);
    entity.setLabel("label");
    entity.setEnabled(true);
    entity.setTransportType("SSE");

    when(repository.findByEnabledTrue()).thenReturn(List.of(entity));

    List<PlatformMcpServerDto> result = provider.findByEnabledTrue();

    assertEquals(1, result.size());
    assertTrue(result.getFirst().enabled());
  }

  @Test
  @DisplayName("findAll retrieves all servers")
  void findAll() {
    PlatformMcpServerEntity entity = new PlatformMcpServerEntity();
    entity.setId(1);
    entity.setLabel("label");
    entity.setEnabled(true);
    entity.setTransportType("SSE");

    when(repository.findAll()).thenReturn(List.of(entity));

    List<PlatformMcpServerDto> result = provider.findAll();

    assertEquals(1, result.size());
  }

  @Test
  @DisplayName("findById retrieves server by ID")
  void findById() {
    PlatformMcpServerEntity entity = new PlatformMcpServerEntity();
    entity.setId(1);
    entity.setLabel("label");
    entity.setEnabled(true);
    entity.setTransportType("SSE");

    when(repository.findById(1)).thenReturn(Optional.of(entity));

    Optional<PlatformMcpServerDto> result = provider.findById(1);

    assertTrue(result.isPresent());
    assertEquals(1, result.get().id());
  }

  @Test
  @DisplayName("save persists server details")
  void save() {
    Instant now = Instant.now(FIXED_CLOCK);
    PlatformMcpServerDto dto =
        new PlatformMcpServerDto(1, "MCP", "SSE", "url", "cmd", "args", "token", true, now);
    PlatformMcpServerEntity entity = provider.toEntity(dto);

    when(repository.save(any(PlatformMcpServerEntity.class))).thenReturn(entity);

    PlatformMcpServerDto saved = provider.save(dto);

    assertNotNull(saved);
    assertEquals("MCP", saved.label());
  }

  @Test
  @DisplayName("deleteById deletes server")
  void deleteById() {
    doNothing().when(repository).deleteById(1);
    provider.deleteById(1);
    verify(repository).deleteById(1);
  }
}
