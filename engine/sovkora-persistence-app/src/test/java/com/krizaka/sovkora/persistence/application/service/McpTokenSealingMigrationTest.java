package com.krizaka.sovkora.persistence.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.assets.infrastructure.adapter.FileMasterKeyProvider;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PlatformMcpServerEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.UserMcpServerEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.PlatformMcpServerRepository;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.UserMcpServerRepository;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

/** The tokens stored in clear before sovkora-engine#58 are sealed once, at startup. */
class McpTokenSealingMigrationTest {

  private static final String USER = "550e8400-e29b-41d4-a716-446655440002";

  @TempDir Path dir;

  @SuppressWarnings("unchecked")
  private McpTokenSealer sealer(boolean withKeyring) throws Exception {
    ObjectProvider<MasterKeyProvider> provided = mock(ObjectProvider.class);
    if (withKeyring) {
      Path keyring = dir.resolve("master.key");
      FileMasterKeyProvider.addKey(keyring, "k1");
      when(provided.getIfAvailable()).thenReturn(new FileMasterKeyProvider(keyring));
    }
    return new McpTokenSealer(provided);
  }

  @Test
  @DisplayName("clear tokens are sealed, sealed ones are left alone, and they open for their owner")
  void clearTokensAreSealedOnce() throws Exception {
    McpTokenSealer tokens = sealer(true);
    UserMcpServerEntity clear = new UserMcpServerEntity();
    clear.setUserId(USER);
    clear.setAuthToken("user-token");
    UserMcpServerEntity alreadySealed = new UserMcpServerEntity();
    alreadySealed.setUserId(USER);
    alreadySealed.setAuthToken(tokens.seal(USER, "other"));
    String before = alreadySealed.getAuthToken();
    PlatformMcpServerEntity platformClear = new PlatformMcpServerEntity();
    platformClear.setAuthToken("platform-token");
    UserMcpServerRepository users = mock(UserMcpServerRepository.class);
    PlatformMcpServerRepository platform = mock(PlatformMcpServerRepository.class);
    when(users.findAll()).thenReturn(List.of(clear, alreadySealed));
    when(platform.findAll()).thenReturn(List.of(platformClear));

    new McpTokenSealingMigration(users, platform, tokens).run(null);

    assertThat(clear.getAuthToken()).startsWith("v1.").doesNotContain("user-token");
    assertThat(tokens.open(USER, clear.getAuthToken())).isEqualTo("user-token");
    assertThat(alreadySealed.getAuthToken()).isEqualTo(before);
    assertThat(tokens.open(null, platformClear.getAuthToken())).isEqualTo("platform-token");
    verify(users).save(clear);
    verify(users, never()).save(alreadySealed);
  }

  @Test
  @DisplayName("a token sealed for one user does not open for another")
  void aSealedTokenOpensOnlyForItsOwner() throws Exception {
    McpTokenSealer tokens = sealer(true);
    String sealed = tokens.seal(USER, "user-token");

    assertThatThrownBy(() -> tokens.open("550e8400-e29b-41d4-a716-446655440099", sealed))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("user-token");
  }

  @Test
  @DisplayName("without a keyring nothing is written in clear, and startup goes on")
  void withoutAKeyringNothingIsWrittenInClear() throws Exception {
    McpTokenSealer tokens = sealer(false);
    UserMcpServerEntity clear = new UserMcpServerEntity();
    clear.setUserId(USER);
    clear.setAuthToken("user-token");
    UserMcpServerRepository users = mock(UserMcpServerRepository.class);
    PlatformMcpServerRepository platform = mock(PlatformMcpServerRepository.class);
    when(users.findAll()).thenReturn(List.of(clear));

    new McpTokenSealingMigration(users, platform, tokens).run(null);

    verify(users, never()).save(any());
    assertThatThrownBy(() -> tokens.seal(USER, "new-token"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no master keyring");
  }
}
