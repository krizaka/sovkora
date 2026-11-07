package com.krizaka.sovkora.adapter.springai.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the {@code /v1} base-url normalisation. The {@code com.openai} SDK appends {@code
 * /chat/completions} to the base-url without inserting the API version, while {@code ai_providers}
 * stores the host root (Ollama's native management API lives there, not under {@code /v1}). So the
 * proxy must append {@code /v1} for the chat client — and must do so idempotently — otherwise every
 * chat call 404s ("Connection to the AI model failed").
 */
class UniversalProxyChatProviderTest {

  @Test
  void withApiVersion_appendsV1WhenMissing() {
    assertThat(UniversalProxyChatProvider.withApiVersion("http://localhost:11434"))
        .isEqualTo("http://localhost:11434/v1");
  }

  @Test
  void withApiVersion_stripsTrailingSlashThenAppends() {
    assertThat(UniversalProxyChatProvider.withApiVersion("http://localhost:8086/"))
        .isEqualTo("http://localhost:8086/v1");
  }

  @Test
  void withApiVersion_leavesExistingV1Untouched() {
    assertThat(UniversalProxyChatProvider.withApiVersion("https://api.openai.com/v1"))
        .isEqualTo("https://api.openai.com/v1");
  }

  @Test
  void withApiVersion_leavesV1InPathUntouched() {
    assertThat(UniversalProxyChatProvider.withApiVersion("http://host:9000/v1/proxy"))
        .isEqualTo("http://host:9000/v1/proxy");
  }

  @Test
  void withApiVersion_passesThroughNullAndBlank() {
    assertThat(UniversalProxyChatProvider.withApiVersion(null)).isNull();
    assertThat(UniversalProxyChatProvider.withApiVersion("   ")).isEqualTo("   ");
  }

  @Test
  void chatModel_buildsAModelForAVersionlessBaseUrl() {
    var model =
        new UniversalProxyChatProvider().chatModel("http://localhost:11434", "not-required", "m");
    assertThat(model).isNotNull();
  }
}
