package com.krizaka.sovkora.adapter.springai.prompt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

class OpenAiOptionsStrategyTest {

  private final OpenAiOptionsStrategy strategy = new OpenAiOptionsStrategy();

  @Test
  void supports_openai_returnsTrue() {
    assertTrue(strategy.supports("openai"));
  }

  @Test
  void supports_openaiCaseInsensitive_returnsTrue() {
    assertTrue(strategy.supports("OPENAI"));
    assertTrue(strategy.supports("OpenAI"));
  }

  @Test
  void supports_otherProvider_returnsFalse() {
    assertFalse(strategy.supports("ollama"));
    assertFalse(strategy.supports("anthropic"));
  }

  @Test
  void buildOptions_returnsOpenAiOptions() {
    var result = strategy.buildOptions("gpt-4", 0.5);
    assertInstanceOf(OpenAiChatOptions.class, result);
    var openaiOpts = (OpenAiChatOptions) result;
    assertEquals("gpt-4", openaiOpts.getModel());
    assertEquals(0.5, openaiOpts.getTemperature());
  }

  @Test
  void buildOptions_nullTemperature_acceptsIt() {
    var result = strategy.buildOptions("gpt-3.5-turbo", null);
    assertInstanceOf(OpenAiChatOptions.class, result);
  }
}
