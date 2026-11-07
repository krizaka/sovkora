package com.krizaka.sovkora.adapter.springai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeminiDynamicProviderTest {

  private final GeminiDynamicProvider provider = new GeminiDynamicProvider();

  @Test
  void supports_gemini() {
    assertThat(provider.supports("gemini")).isTrue();
  }

  @Test
  void supports_google() {
    assertThat(provider.supports("google")).isTrue();
  }

  @Test
  void supports_caseInsensitive() {
    assertThat(provider.supports("GEMINI")).isTrue();
    assertThat(provider.supports("Google")).isTrue();
  }

  @Test
  void doesNotSupport_otherProviders() {
    assertThat(provider.supports("claude")).isFalse();
    assertThat(provider.supports("ollama")).isFalse();
    assertThat(provider.supports("openai")).isFalse();
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
    var model = provider.create("gemini-2.0-flash", "test-key");
    assertThat(model).isNotNull();
  }
}
