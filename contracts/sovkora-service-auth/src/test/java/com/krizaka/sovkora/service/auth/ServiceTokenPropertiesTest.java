package com.krizaka.sovkora.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ServiceTokenPropertiesTest {

  @Test
  void refusesAMissingKeyAndNamesTheVariable() {
    assertThatThrownBy(() -> new ServiceTokenProperties(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AUTH_SERVICE_TOKEN_SECRET is not set");
    assertThatThrownBy(() -> new ServiceTokenProperties("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AUTH_SERVICE_TOKEN_SECRET is not set");
  }

  @Test
  void refusesAKeyShorterThan256Bits() {
    assertThatThrownBy(() -> new ServiceTokenProperties("short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 32 characters");
  }

  @Test
  void neverPrintsTheKey() {
    assertThat(new ServiceTokenProperties(TokenSigner.SERVICE_KEY).toString())
        .doesNotContain(TokenSigner.SERVICE_KEY);
  }
}
