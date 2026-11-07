package com.krizaka.sovkora.automationservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The service seals a trigger's secrets with the platform's master keyring: its path must be
 * configured, never defaulted unresolved.
 */
class AssetEncryptionConfigTest {

  @Test
  @DisplayName(
      "application.yml names the master keyring file, resolved from the user's home when nothing overrides it")
  void masterKeyFileIsConfigured() throws IOException {
    String yaml =
        new String(
            new ClassPathResource("application.yml").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains("master-key-file: ${ASSETS_MASTER_KEY_FILE:${user.home}/.sovkora/master.key}")
        .contains("enabled: ${ASSETS_ENCRYPTION_ENABLED:true}")
        .contains("accept-plaintext: ${ASSETS_ACCEPT_PLAINTEXT:false}");
  }
}
