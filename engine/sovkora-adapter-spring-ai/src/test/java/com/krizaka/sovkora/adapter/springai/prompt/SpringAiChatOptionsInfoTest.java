package com.krizaka.sovkora.adapter.springai.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

class SpringAiChatOptionsInfoTest {

  @Test
  void resolveFromOptions_ollamaOptions_returnsOllama() {
    assertEquals("ollama", SpringAiChatOptionsInfo.providerOf(OllamaChatOptions.builder().build()));
  }

  @Test
  void resolveFromOptions_openaiOptions_returnsOpenai() {
    assertEquals("openai", SpringAiChatOptionsInfo.providerOf(OpenAiChatOptions.builder().build()));
  }

  @Test
  void resolveFromOptions_anthropicOptions_returnsAnthropic() {
    assertEquals(
        "anthropic", SpringAiChatOptionsInfo.providerOf(AnthropicChatOptions.builder().build()));
  }

  @Test
  void resolveFromOptions_geminiOptions_returnsGemini() {
    assertEquals(
        "gemini", SpringAiChatOptionsInfo.providerOf(GoogleGenAiChatOptions.builder().build()));
  }

  @Test
  void resolveFromOptions_null_returnsNull() {
    assertNull(SpringAiChatOptionsInfo.providerOf(null));
  }

  @Test
  void resolveModelName_openaiOptions_returnsModelName() {
    var options = OpenAiChatOptions.builder().model("gpt-4").build();
    assertEquals("gpt-4", SpringAiChatOptionsInfo.modelNameOf(options));
  }

  @Test
  void resolveModelName_anthropicOptions_returnsModelName() {
    var options = AnthropicChatOptions.builder().model("claude-3").build();
    assertEquals("claude-3", SpringAiChatOptionsInfo.modelNameOf(options));
  }

  @Test
  void resolveModelName_geminiOptions_returnsModelName() {
    var options = GoogleGenAiChatOptions.builder().model("gemini-pro").build();
    assertEquals("gemini-pro", SpringAiChatOptionsInfo.modelNameOf(options));
  }

  @Test
  void resolveModelName_ollamaOptions_returnsModelName() {
    var options = OllamaChatOptions.builder().model("llama3").build();
    assertEquals("llama3", SpringAiChatOptionsInfo.modelNameOf(options));
  }

  @Test
  void resolveModelName_null_returnsNull() {
    assertNull(SpringAiChatOptionsInfo.modelNameOf(null));
  }
}
