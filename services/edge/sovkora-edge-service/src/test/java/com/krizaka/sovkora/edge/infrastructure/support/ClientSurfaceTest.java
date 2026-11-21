package com.krizaka.sovkora.edge.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientSurfaceTest {

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/jobs", "/api/v1/auth/login", "/hooks/6f1c2b1e-0000-4000-8000-1"})
  @DisplayName("[r348] the client API and the signed webhooks are forwarded")
  void theClientSurfacesAreForwarded(String path) {
    assertThat(ClientSurface.forwards(path)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/",
        "/actuator/health",
        "/actuator/env",
        "/actuator/info",
        "/internal/v1/capabilities",
        "/api",
        "/apis/v1/jobs",
        "/API/v1/jobs",
        "/hooks",
        "/hooksx/1",
        "//api/v1/jobs"
      })
  @DisplayName("[r348] every other path is not forwarded")
  void everyOtherPathIsNotForwarded(String path) {
    assertThat(ClientSurface.forwards(path)).isFalse();
  }
}
