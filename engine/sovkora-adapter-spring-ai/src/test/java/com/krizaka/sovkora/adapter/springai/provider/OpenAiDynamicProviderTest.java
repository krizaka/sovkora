package com.krizaka.sovkora.adapter.springai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpenAiDynamicProviderTest {

  private final OpenAiDynamicProvider provider = new OpenAiDynamicProvider();

  @Test
  void supports_openai() {
    assertThat(provider.supports("openai")).isTrue();
  }

  @Test
  void supports_caseInsensitive() {
    assertThat(provider.supports("OpenAI")).isTrue();
    assertThat(provider.supports("OPENAI")).isTrue();
  }

  @Test
  void doesNotSupport_otherProviders() {
    assertThat(provider.supports("claude")).isFalse();
    assertThat(provider.supports("ollama")).isFalse();
    assertThat(provider.supports("gemini")).isFalse();
  }

  @Test
  void create_rejectsNullModelName() {
    assertThatThrownBy(() -> provider.create(null, "api-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Model name is required");
  }

  @Test
  void create_rejectsBlankModelName() {
    assertThatThrownBy(() -> provider.create("   ", "api-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Model name is required");
  }

  @Test
  void create_returnsChatModel() {
    var model = provider.create("gpt-4o", "test-key");
    assertThat(model).isNotNull();
  }
}
