package com.krizaka.sovkora.test.support.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretDefaultRulesTest {

  @Test
  @DisplayName("a secret with a literal default is found, an empty or a nested one is not")
  void findsOnlyLiteralDefaults() {
    String yaml =
        """
        a: ${IDENTITY_JWT_SECRET:a-value-anyone-can-read}
        b: ${IDENTITY_JWT_SECRET:}
        c: ${JOB_POD_TOKEN_SECRET:${IDENTITY_JWT_SECRET:}}
        d: ${AUTH_SERVICE_TOKEN_SECRET}
        e: ${RABBITMQ_HOST:localhost}
        """;

    assertThat(SecretDefaultRules.defaultedSecrets(yaml)).containsExactly("IDENTITY_JWT_SECRET");
  }

  @Test
  @DisplayName("a nested secret default is found")
  void findsANestedDefault() {
    assertThat(SecretDefaultRules.defaultedSecrets("x: ${A_SECRET:${B_SECRET:literal}}"))
        .containsExactly("B_SECRET");
  }

  @Test
  @DisplayName("the rule fails on a folder whose configuration defaults a secret")
  void failsOnAFolder(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("application.yml"), "s: ${IDENTITY_JWT_SECRET:literal}\n");
    Files.writeString(dir.resolve("other.yml"), "s: ${OTHER_SECRET:ignored-not-app-config}\n");

    assertThatThrownBy(() -> SecretDefaultRules.assertNoSecretHasADefault(dir))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("application.yml: IDENTITY_JWT_SECRET")
        .hasMessageNotContaining("OTHER_SECRET");
  }

  @Test
  @DisplayName("the rule passes on a folder whose secrets come from the environment")
  void passesOnAFolder(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("application-e2e.yml"), "s: ${IDENTITY_JWT_SECRET:}\n");

    assertThatCode(() -> SecretDefaultRules.assertNoSecretHasADefault(dir))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("the rule fails on a folder with no application configuration: it judged nothing")
  void failsOnNothing(@TempDir Path dir) {
    assertThatThrownBy(() -> SecretDefaultRules.assertNoSecretHasADefault(dir))
        .hasMessageContaining("CFG-002");
  }
}
