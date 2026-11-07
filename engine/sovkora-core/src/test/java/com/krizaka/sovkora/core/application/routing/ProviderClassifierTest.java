package com.krizaka.sovkora.core.application.routing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderClassifierTest {

  @Test
  void ollama_returnsOllama() {
    assertEquals("ollama", ProviderClassifier.ollama());
  }

  @Test
  void openai_returnsOpenai() {
    assertEquals("openai", ProviderClassifier.openai());
  }

  @Test
  void anthropic_returnsAnthropic() {
    assertEquals("anthropic", ProviderClassifier.anthropic());
  }

  @Test
  void gemini_returnsGemini() {
    assertEquals("gemini", ProviderClassifier.gemini());
  }

  @ParameterizedTest
  @ValueSource(strings = {"openai", "claude", "anthropic", "gemini", "google"})
  void isCommercial_commercialProviders_returnsTrue(String provider) {
    assertTrue(ProviderClassifier.isCommercial(provider));
  }

  @ParameterizedTest
  @ValueSource(strings = {"OPENAI", "Claude", "ANTHROPIC", "Gemini", "GOOGLE"})
  void isCommercial_caseInsensitive(String provider) {
    assertTrue(ProviderClassifier.isCommercial(provider));
  }

  @Test
  void isCommercial_localProvider_returnsFalse() {
    assertFalse(ProviderClassifier.isCommercial("ollama"));
  }

  @Test
  void isCommercial_null_returnsFalse() {
    assertFalse(ProviderClassifier.isCommercial(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ollama", "localai"})
  void isLocal_localProviders_returnsTrue(String provider) {
    assertTrue(ProviderClassifier.isLocal(provider));
  }

  @Test
  void isLocal_commercialProvider_returnsFalse() {
    assertFalse(ProviderClassifier.isLocal("openai"));
  }

  @Test
  void isLocal_null_returnsFalse() {
    assertFalse(ProviderClassifier.isLocal(null));
  }
}
