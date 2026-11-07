package com.krizaka.sovkora.persistence.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.persistence.domain.model.UserMcpServerDto;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.UserMcpServerEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.UserMcpServerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserMcpServerPersistenceProviderImplTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Mock private UserMcpServerRepository repository;

  @org.junit.jupiter.api.io.TempDir java.nio.file.Path keyringDir;

  private McpTokenSealer tokens;
  private UserMcpServerPersistenceProviderImpl provider;

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
    provider = new UserMcpServerPersistenceProviderImpl(repository, tokens);
  }

  @Test
  @DisplayName("[#58] a token is stored sealed, never in clear, and reads back for the client")
  void theTokenIsStoredSealed() {
    UserMcpServerDto dto =
        new UserMcpServerDto(
            1,
            "550e8400-e29b-41d4-a716-446655440002",
            "mcp",
            "https://mcp.example",
            "secret-token",
            true,
            Instant.now(FIXED_CLOCK));
    org.mockito.ArgumentCaptor<UserMcpServerEntity> stored =
        org.mockito.ArgumentCaptor.forClass(UserMcpServerEntity.class);
    when(repository.save(stored.capture())).thenAnswer(call -> call.getArgument(0));

    UserMcpServerDto saved = provider.save(dto);

    assertThat(stored.getValue().getAuthToken())
        .isNotNull()
        .doesNotContain("secret-token")
        .startsWith("v1.");
    assertThat(saved.authToken()).isEqualTo("secret-token");
  }

  @Test
  @DisplayName("[#58] a token shaped like a sealed value is still sealed on save, and reads back")
  void aSealedLookingTokenIsStillSealed() {
    UserMcpServerDto dto =
        new UserMcpServerDto(
            1,
            "550e8400-e29b-41d4-a716-446655440002",
            "mcp",
            "https://mcp.example",
            "v1.a.b.c.d",
            true,
            null);
    org.mockito.ArgumentCaptor<UserMcpServerEntity> stored =
        org.mockito.ArgumentCaptor.forClass(UserMcpServerEntity.class);
    when(repository.save(stored.capture())).thenAnswer(call -> call.getArgument(0));

    UserMcpServerDto saved = provider.save(dto);

    assertThat(stored.getValue().getAuthToken()).isNotEqualTo("v1.a.b.c.d").startsWith("v1.");
    assertThat(saved.authToken()).isEqualTo("v1.a.b.c.d");
  }

  @Test
  @DisplayName(
      "[#58] a token of 1000 characters is sealed and reads back (the column holds the envelope)")
  void aLongTokenFits() {
    String longToken = "t".repeat(1000);
    UserMcpServerDto dto =
        new UserMcpServerDto(
            1,
            "550e8400-e29b-41d4-a716-446655440002",
            "mcp",
            "https://mcp.example",
            longToken,
            true,
            null);
    org.mockito.ArgumentCaptor<UserMcpServerEntity> stored =
        org.mockito.ArgumentCaptor.forClass(UserMcpServerEntity.class);
    when(repository.save(stored.capture())).thenAnswer(call -> call.getArgument(0));

    assertThat(provider.save(dto).authToken()).isEqualTo(longToken);
    assertThat(stored.getValue().getAuthToken().length()).isGreaterThan(1000);
  }

  @Test
  @DisplayName("[#58] a row written in clear before #58 still reads, until the migration seals it")
  void aLegacyClearRowStillReads() {
    UserMcpServerEntity legacy = new UserMcpServerEntity();
    legacy.setId(7);
    legacy.setUserId("user-1");
    legacy.setLabel("legacy");
    legacy.setUrl("https://mcp.example");
    legacy.setAuthToken("legacy-token");
    when(repository.findById(7)).thenReturn(java.util.Optional.of(legacy));

    assertThat(provider.findById(7)).map(UserMcpServerDto::authToken).contains("legacy-token");
  }

  @Test
  @DisplayName("Constructor throws NullPointerException on null repository")
  void constructorValidation() {
    assertThatThrownBy(() -> new UserMcpServerPersistenceProviderImpl(null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("UserMcpServerRepository cannot be null");
  }

  @Test
  @DisplayName("findByUserIdAndEnabledTrue retrieves and maps entities")
  void findByUserIdAndEnabledTrue() {
    UserMcpServerEntity entity = new UserMcpServerEntity();
    entity.setId(1);
    entity.setUserId("user-1");
    entity.setLabel("mcp-server");
    entity.setUrl("http://localhost");
    entity.setAuthToken("token");
    entity.setEnabled(true);
    entity.setCreatedAt(Instant.now(FIXED_CLOCK));

    when(repository.findByUserIdAndEnabledTrue("user-1")).thenReturn(List.of(entity));

    List<UserMcpServerDto> result = provider.findByUserIdAndEnabledTrue("user-1");

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().label()).isEqualTo("mcp-server");
    verify(repository).findByUserIdAndEnabledTrue("user-1");
  }

  @Test
  @DisplayName("findByUserId retrieves and maps entities")
  void findByUserId() {
    UserMcpServerEntity entity = new UserMcpServerEntity();
    entity.setId(1);
    entity.setUserId("user-1");
    entity.setLabel("mcp-server");
    entity.setUrl("http://localhost");
    entity.setEnabled(true);

    when(repository.findByUserId("user-1")).thenReturn(List.of(entity));

    List<UserMcpServerDto> result = provider.findByUserId("user-1");

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().label()).isEqualTo("mcp-server");
  }

  @Test
  @DisplayName("findById retrieves and maps entity if present")
  void findById() {
    UserMcpServerEntity entity = new UserMcpServerEntity();
    entity.setId(1);
    entity.setUserId("user-1");
    entity.setLabel("mcp-server");
    entity.setUrl("http://localhost");
    entity.setEnabled(true);

    when(repository.findById(1)).thenReturn(Optional.of(entity));

    Optional<UserMcpServerDto> result = provider.findById(1);

    assertThat(result).isPresent();
    assertThat(result.get().label()).isEqualTo("mcp-server");
  }

  @Test
  @DisplayName("save persists and maps UserMcpServerDto")
  void save() {
    UserMcpServerDto dto =
        new UserMcpServerDto(
            1, "user-1", "mcp-server", "http://localhost", "token", true, Instant.now(FIXED_CLOCK));
    UserMcpServerEntity entity = provider.toEntity(dto);
    when(repository.save(any(UserMcpServerEntity.class))).thenReturn(entity);

    UserMcpServerDto result = provider.save(dto);

    assertThat(result).isNotNull();
    assertThat(result.label()).isEqualTo("mcp-server");
    verify(repository).save(any(UserMcpServerEntity.class));
  }

  @Test
  @DisplayName("deleteById deletes entity")
  void delete() {
    doNothing().when(repository).deleteById(1);

    provider.deleteById(1);

    verify(repository).deleteById(1);
  }
}
