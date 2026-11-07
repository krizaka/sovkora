package com.krizaka.sovkora.persistence.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.assets.infrastructure.adapter.FileMasterKeyProvider;
import com.krizaka.sovkora.crypto.SecretSealer;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

@DisplayName("a provider's platform key is read from its sealed setting, and nowhere else")
class SealedCheckKeyStoreAdapterTest {

  @TempDir Path dir;

  private MasterKeyProvider keys;
  private final Map<String, String> rows = new HashMap<>();
  private final RuntimeConfigProvider settings =
      new RuntimeConfigProvider() {
        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
          return defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
          return defaultValue;
        }

        @Override
        public String getString(String key, String defaultValue) {
          return rows.getOrDefault(key, defaultValue);
        }
      };

  @BeforeEach
  void keyring() throws Exception {
    Path keyFile = dir.resolve("master.key");
    FileMasterKeyProvider.addKey(keyFile, "k1");
    keys = new FileMasterKeyProvider(keyFile);
  }

  private SealedCheckKeyStoreAdapter store(MasterKeyProvider keyring) {
    StaticListableBeanFactory beans = new StaticListableBeanFactory();
    if (keyring != null) {
      beans.addBean("keys", keyring);
    }
    ObjectProvider<MasterKeyProvider> provider = beans.getBeanProvider(MasterKeyProvider.class);
    return new SealedCheckKeyStoreAdapter(settings, provider);
  }

  @Test
  @DisplayName("a key sealed for its provider opens")
  void opens() {
    rows.put(
        SealedCheckKeyStoreAdapter.settingKey("soquij"),
        new SecretSealer(keys).seal(SealedCheckKeyStoreAdapter.scope("soquij"), "the-key"));

    assertThat(store(keys).platformKey("soquij")).contains("the-key");
  }

  @Test
  @DisplayName("a key sealed for another provider, in clear, absent, or with no keyring is no key")
  void failsClosed() {
    rows.put(
        SealedCheckKeyStoreAdapter.settingKey("soquij"),
        new SecretSealer(keys).seal(SealedCheckKeyStoreAdapter.scope("certn"), "the-key"));
    rows.put(SealedCheckKeyStoreAdapter.settingKey("trustii"), "plain-in-clear");
    rows.put(
        SealedCheckKeyStoreAdapter.settingKey("certn"),
        new SecretSealer(keys).seal(SealedCheckKeyStoreAdapter.scope("certn"), "k"));

    assertThat(store(keys).platformKey("soquij")).isEmpty();
    assertThat(store(keys).platformKey("trustii")).isEmpty();
    assertThat(store(keys).platformKey("absent")).isEmpty();
    assertThat(store(null).platformKey("certn")).isEmpty();
  }

  @Test
  @DisplayName("the store never prints a key, and a user key has no store yet")
  void noLeakNoUserKey() {
    assertThat(store(keys).toString()).doesNotContain("key=");
    assertThat(store(keys).userKey(UUID.randomUUID().toString(), "soquij")).isEmpty();
  }
}
