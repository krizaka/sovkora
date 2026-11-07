package com.krizaka.sovkora.engine.persistence.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.crypto.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.crypto.infrastructure.adapter.FileMasterKeyProvider;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

/** The sealer reads the keyring once, seals for an owner, and never stores a token in clear. */
class McpTokenSealerTest {

  private static final String USER = "550e8400-e29b-41d4-a716-446655440002";

  @TempDir Path dir;

  @SuppressWarnings("unchecked")
  private static ObjectProvider<MasterKeyProvider> provider() {
    return mock(ObjectProvider.class);
  }

  private MasterKeyProvider keyring() throws Exception {
    Path file = dir.resolve("master.key");
    FileMasterKeyProvider.addKey(file, "k1");
    return new FileMasterKeyProvider(file);
  }

  @Test
  @DisplayName("the keyring is looked up once and the same sealer serves every later call")
  void looksUpTheKeyringOnce() throws Exception {
    ObjectProvider<MasterKeyProvider> keys = provider();
    when(keys.getIfAvailable()).thenReturn(keyring());
    McpTokenSealer tokens = new McpTokenSealer(keys);

    String sealed = tokens.seal(USER, "user-token");
    String again = tokens.seal(null, "platform-token");

    assertThat(tokens.open(USER, sealed)).isEqualTo("user-token");
    assertThat(tokens.open(null, again)).isEqualTo("platform-token");
    verify(keys, times(1)).getIfAvailable();
  }

  @Test
  @DisplayName("without a keyring a seal fails, and a keyring that appears later is used")
  void aMissingKeyringIsNotRemembered() throws Exception {
    ObjectProvider<MasterKeyProvider> keys = provider();
    when(keys.getIfAvailable()).thenReturn(null).thenReturn(keyring());
    McpTokenSealer tokens = new McpTokenSealer(keys);

    assertThatThrownBy(() -> tokens.seal(USER, "user-token"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no master keyring");
    assertThat(tokens.seal(USER, "user-token")).startsWith("v1.");
  }

  @Test
  @DisplayName("callers racing on the first use all get a sealer that opens what the others sealed")
  void concurrentFirstUseIsSafe() throws Exception {
    ObjectProvider<MasterKeyProvider> keys = provider();
    when(keys.getIfAvailable()).thenReturn(keyring());
    McpTokenSealer tokens = new McpTokenSealer(keys);
    List<Callable<String>> calls = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      String token = "token-" + i;
      calls.add(() -> tokens.seal(USER, token));
    }

    List<String> sealed = new ArrayList<>();
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      for (Future<String> result : pool.invokeAll(calls)) {
        sealed.add(result.get());
      }
    }

    for (int i = 0; i < sealed.size(); i++) {
      assertThat(tokens.open(USER, sealed.get(i))).isEqualTo("token-" + i);
    }
  }

  @Test
  @DisplayName("no token is no token, and a value stored before sealing opens as it is")
  void blankAndLegacyValues() {
    McpTokenSealer tokens = new McpTokenSealer(provider());

    assertThat(tokens.seal(USER, " ")).isNull();
    assertThat(tokens.open(USER, null)).isNull();
    assertThat(tokens.open(USER, "legacy-clear-token")).isEqualTo("legacy-clear-token");
  }
}
