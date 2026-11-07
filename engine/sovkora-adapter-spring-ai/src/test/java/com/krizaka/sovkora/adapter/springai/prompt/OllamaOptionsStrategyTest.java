package com.krizaka.sovkora.adapter.springai.prompt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaChatOptions;

class OllamaOptionsStrategyTest {

  private final OllamaOptionsStrategy strategy = new OllamaOptionsStrategy();

  @Test
  void supports_ollama_returnsTrue() {
    assertTrue(strategy.supports("ollama"));
  }

  @Test
  void supports_ollamaCaseInsensitive_returnsTrue() {
    assertTrue(strategy.supports("OLLAMA"));
    assertTrue(strategy.supports("Ollama"));
  }

  @Test
  void supports_otherProvider_returnsFalse() {
    assertFalse(strategy.supports("openai"));
    assertFalse(strategy.supports("anthropic"));
  }

  @Test
  void buildOptions_returnsOllamaOptions() {
    var result = strategy.buildOptions("llama3", 0.7);
    assertInstanceOf(OllamaChatOptions.class, result);
    var ollamaOpts = (OllamaChatOptions) result;
    assertEquals("llama3", ollamaOpts.getModel());
    assertEquals(0.7, ollamaOpts.getTemperature());
  }

  @Test
  void buildOptions_nullTemperature_acceptsIt() {
    var result = strategy.buildOptions("mistral", null);
    assertInstanceOf(OllamaChatOptions.class, result);
  }
}
