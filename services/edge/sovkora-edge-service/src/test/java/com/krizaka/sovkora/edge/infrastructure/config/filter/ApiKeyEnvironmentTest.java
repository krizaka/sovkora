package com.krizaka.sovkora.edge.infrastructure.config.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The environment a key declares (an {@code env:} scope) against the one the edge declares. */
class ApiKeyEnvironmentTest {

  @Test
  @DisplayName("[2132] the edge's environment is declared by name, an unknown name fails at start")
  void declaredByName() {
    assertThat(ApiKeyEnvironment.of("prod")).isEqualTo(ApiKeyEnvironment.PROD);
    assertThat(ApiKeyEnvironment.of("dev")).isEqualTo(ApiKeyEnvironment.DEV);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ApiKeyEnvironment.of("staging"))
        .withMessageContaining("staging");
    assertThatIllegalArgumentException().isThrownBy(() -> ApiKeyEnvironment.of(" "));
  }

  @Test
  @DisplayName("[2132] a prod edge admits only a key that declares env:prod")
  void prodAdmitsOnlyProdKeys() {
    assertThat(ApiKeyEnvironment.PROD.admits(List.of("env:prod"))).isTrue();
    assertThat(ApiKeyEnvironment.PROD.admits(List.of("env:prod", "ai:embed"))).isTrue();
    // A key that declares nothing is a dev key: every key minted before #2132 is one.
    assertThat(ApiKeyEnvironment.PROD.admits(List.of())).isFalse();
    assertThat(ApiKeyEnvironment.PROD.admits(null)).isFalse();
    assertThat(ApiKeyEnvironment.PROD.admits(List.of("ai:embed"))).isFalse();
    assertThat(ApiKeyEnvironment.PROD.admits(List.of("env:dev"))).isFalse();
  }

  @Test
  @DisplayName(
      "[2132] a dev edge admits the keys that declare nothing or env:dev, never a prod key")
  void devRefusesProdKeys() {
    assertThat(ApiKeyEnvironment.DEV.admits(List.of())).isTrue();
    assertThat(ApiKeyEnvironment.DEV.admits(List.of("ai:embed"))).isTrue();
    assertThat(ApiKeyEnvironment.DEV.admits(List.of("env:dev"))).isTrue();
    assertThat(ApiKeyEnvironment.DEV.admits(List.of("env:prod"))).isFalse();
  }

  @Test
  @DisplayName(
      "[2132] an unknown or a second environment scope is refused everywhere (fail closed)")
  void ambiguousKeysAreRefused() {
    for (ApiKeyEnvironment edge : ApiKeyEnvironment.values()) {
      assertThat(edge.admits(List.of("env:qa"))).isFalse();
      assertThat(edge.admits(List.of("env:prod", "env:dev"))).isFalse();
    }
  }

  @Test
  @DisplayName("[2132] an environment scope is not a route scope")
  void environmentScopeIsRecognised() {
    assertThat(ApiKeyEnvironment.isEnvironmentScope("env:prod")).isTrue();
    assertThat(ApiKeyEnvironment.isEnvironmentScope("ai:embed")).isFalse();
  }
}
