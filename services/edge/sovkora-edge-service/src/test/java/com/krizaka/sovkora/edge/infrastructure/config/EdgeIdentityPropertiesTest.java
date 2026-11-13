package com.krizaka.sovkora.edge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EdgeIdentityPropertiesTest {

  @Test
  @DisplayName("Valid wiring is accepted")
  void validWiring() {
    var properties =
        new EdgeIdentityProperties(
            URI.create("http://identity:8083"), "sovkora-test-secret-at-least-32-characters!");
    assertThat(properties.baseUrl()).isEqualTo(URI.create("http://identity:8083"));
  }

  @Test
  @DisplayName("Relative and null URLs are rejected")
  void invalidWiringRejected() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new EdgeIdentityProperties(null, "sovkora-test-secret-at-least-32-characters!"));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new EdgeIdentityProperties(
                    URI.create("/relative"), "sovkora-test-secret-at-least-32-characters!"));
  }

  @Test
  @DisplayName(
      "[2132] the edge's key environment is declared; a blank one fails at start, the short form is dev")
  void apiKeyEnvironmentIsDeclared() {
    URI identity = URI.create("http://localhost:8083");
    String secret = "sovkora-test-secret-at-least-32-characters!";
    assertThat(new EdgeIdentityProperties(identity, secret, "prod").apiKeyEnvironment())
        .isEqualTo("prod");
    assertThat(new EdgeIdentityProperties(identity, secret).apiKeyEnvironment()).isEqualTo("dev");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EdgeIdentityProperties(identity, secret, " "))
        .withMessageContaining("api-key-environment");
  }
}
