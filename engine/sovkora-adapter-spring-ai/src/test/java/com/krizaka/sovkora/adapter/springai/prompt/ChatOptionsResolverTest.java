package com.krizaka.sovkora.adapter.springai.prompt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

class ChatOptionsResolverTest {

  private static final String DEFAULT_MODEL = "llama3";

  @Test
  void resolveOllamaOptions_nullOptions_returnsDefaults() {
    ChatOptions result = ChatOptionsResolver.resolveOllamaOptions(null, DEFAULT_MODEL);
    assertInstanceOf(OllamaChatOptions.class, result);
    assertEquals(DEFAULT_MODEL, ((OllamaChatOptions) result).getModel());
  }

  @Test
  void resolveOllamaOptions_openaiOptions_withGptModel_usesOllamaModel() {
    var openai = OpenAiChatOptions.builder().model("gpt-4").temperature(0.5).build();
    ChatOptions result = ChatOptionsResolver.resolveOllamaOptions(openai, DEFAULT_MODEL);
    assertInstanceOf(OllamaChatOptions.class, result);
    assertEquals(DEFAULT_MODEL, ((OllamaChatOptions) result).getModel());
  }

  @Test
  void resolveOllamaOptions_openaiOptions_withCustomModel_preservesModel() {
    var openai = OpenAiChatOptions.builder().model("my-model").temperature(0.5).build();
    ChatOptions result = ChatOptionsResolver.resolveOllamaOptions(openai, DEFAULT_MODEL);
    assertInstanceOf(OllamaChatOptions.class, result);
    assertEquals("my-model", ((OllamaChatOptions) result).getModel());
  }

  @Test
  void resolveOllamaOptions_openaiOptions_withNullModel_usesOllamaModel() {
    var openai = OpenAiChatOptions.builder().temperature(0.5).build();
    ChatOptions result = ChatOptionsResolver.resolveOllamaOptions(openai, DEFAULT_MODEL);
    assertInstanceOf(OllamaChatOptions.class, result);
    assertEquals(DEFAULT_MODEL, ((OllamaChatOptions) result).getModel());
  }

  @Test
  void resolveOllamaOptions_ollamaOptions_withBlankModel_usesDefaultModel() {
    var ollamaOpt = OllamaChatOptions.builder().model("").temperature(0.3).build();
    ChatOptions result = ChatOptionsResolver.resolveOllamaOptions(ollamaOpt, DEFAULT_MODEL);
    assertInstanceOf(OllamaChatOptions.class, result);
    assertEquals(DEFAULT_MODEL, ((OllamaChatOptions) result).getModel());
  }

  @Test
  void resolveOllamaOptions_ollamaOptions_withModel_preserves() {
    var ollamaOpt = OllamaChatOptions.builder().model("mistral").temperature(0.3).build();
    ChatOptions result = ChatOptionsResolver.resolveOllamaOptions(ollamaOpt, DEFAULT_MODEL);
    assertSame(ollamaOpt, result);
  }

  @Test
  void resolveOllamaOptions_anthropicOptions_passesThrough() {
    var anthro = AnthropicChatOptions.builder().build();
    ChatOptions result = ChatOptionsResolver.resolveOllamaOptions(anthro, DEFAULT_MODEL);
    assertSame(anthro, result);
  }

  @Test
  void resolveDefaultOptions_openai() {
    ChatOptions result = ChatOptionsResolver.resolveDefaultOptions("openai");
    assertInstanceOf(OpenAiChatOptions.class, result);
  }

  @Test
  void resolveDefaultOptions_anthropic() {
    ChatOptions result = ChatOptionsResolver.resolveDefaultOptions("anthropic");
    assertInstanceOf(AnthropicChatOptions.class, result);
  }

  @Test
  void resolveDefaultOptions_claude() {
    ChatOptions result = ChatOptionsResolver.resolveDefaultOptions("claude");
    assertInstanceOf(AnthropicChatOptions.class, result);
  }

  @Test
  void resolveDefaultOptions_gemini() {
    ChatOptions result = ChatOptionsResolver.resolveDefaultOptions("gemini");
    assertInstanceOf(GoogleGenAiChatOptions.class, result);
  }

  @Test
  void resolveDefaultOptions_google() {
    ChatOptions result = ChatOptionsResolver.resolveDefaultOptions("google");
    assertInstanceOf(GoogleGenAiChatOptions.class, result);
  }

  @Test
  void resolveDefaultOptions_unknown() {
    ChatOptions result = ChatOptionsResolver.resolveDefaultOptions("custom");
    assertNotNull(result);
  }
}
