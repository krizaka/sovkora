package com.krizaka.sovkora.automationservice.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebhookSecretTest {

  @Test
  @DisplayName("a secret is 32 random bytes in the whsec_ form, and is different every time")
  void generates() {
    WebhookSecret secret = WebhookSecret.generate();
    assertThat(secret.reveal()).startsWith("whsec_");
    assertThat(secret.key()).hasSize(32);
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 50; i++) {
      assertThat(seen.add(WebhookSecret.generate().reveal())).isTrue();
    }
  }

  @Test
  @DisplayName("it round-trips through its transport form")
  void roundTrips() {
    WebhookSecret secret = WebhookSecret.generate();
    assertThat(WebhookSecret.of(secret.reveal()).key()).isEqualTo(secret.key());
  }

  @Test
  @DisplayName("what is not a secret of this form is refused")
  void refuses() {
    assertThatThrownBy(() -> WebhookSecret.of(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WebhookSecret.of("nope")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WebhookSecret.of("whsec_@@@"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WebhookSecret.of("whsec_AAAA"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("it never prints itself, and its hint is four characters of it")
  void hides() {
    WebhookSecret secret = WebhookSecret.generate();
    assertThat(secret.toString()).doesNotContain(secret.reveal()).doesNotContain(secret.hint());
    assertThat(secret.hint()).hasSize(4);
    assertThat(secret.reveal()).endsWith(secret.hint());
  }
}
