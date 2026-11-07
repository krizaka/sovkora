package com.krizaka.sovkora.interceptor.reformulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SemanticRouterInterceptorTest {

  private final SemanticRouterInterceptor interceptor = new SemanticRouterInterceptor();

  private PromptContext createContext(String refinedPrompt) {
    return new PromptContext("raw", Map.of(), Map.of(), refinedPrompt, null, null);
  }

  @Test
  void intercept_classifiesChatIntent() {
    PromptContext result = interceptor.intercept(createContext("What is the weather?"));
    assertThat(result.systemMetadata()).containsEntry("resolvedIntent", "CHAT");
  }

  @Test
  void intercept_classifiesImageIntent() {
    PromptContext result = interceptor.intercept(createContext("Generate image of a sunset"));
    assertThat(result.systemMetadata()).containsEntry("resolvedIntent", "IMAGE");
  }

  @Test
  void intercept_classifiesVideoIntent() {
    PromptContext result = interceptor.intercept(createContext("Create video of dancing robots"));
    assertThat(result.systemMetadata()).containsEntry("resolvedIntent", "VIDEO");
  }

  @Test
  void intercept_classifiesSpeechIntent() {
    PromptContext result = interceptor.intercept(createContext("Read aloud this text"));
    assertThat(result.systemMetadata()).containsEntry("resolvedIntent", "SPEECH");
  }

  @Test
  void intercept_classifiesCodeIntent() {
    PromptContext result = interceptor.intercept(createContext("Run code to compute fibonacci"));
    assertThat(result.systemMetadata()).containsEntry("resolvedIntent", "CODE_SANDBOX");
  }

  @Test
  void intercept_defaultsToChatForEmptyPrompt() {
    PromptContext result = interceptor.intercept(createContext(""));
    assertThat(result.systemMetadata()).containsEntry("resolvedIntent", "CHAT");
  }

  @Test
  void intercept_defaultsToChatForNullPrompt() {
    PromptContext result = interceptor.intercept(createContext(null));
    assertThat(result.systemMetadata()).containsEntry("resolvedIntent", "CHAT");
  }

  @Test
  @SuppressWarnings("unchecked")
  void intercept_injectsDagSteps() {
    PromptContext result = interceptor.intercept(createContext("Draw a cat"));
    List<String> steps = (List<String>) result.systemMetadata().get("semanticRouterSteps");
    assertThat(steps).isNotEmpty();
    assertThat(steps.getFirst()).startsWith("IMAGE:");
  }

  @Test
  void intercept_recognizesDrawKeyword() {
    PromptContext result = interceptor.intercept(createContext("Please draw me something"));
    assertThat(result.systemMetadata()).containsEntry("resolvedIntent", "IMAGE");
  }

  @Test
  void intercept_recognizesAnimateKeyword() {
    PromptContext result = interceptor.intercept(createContext("animate the logo"));
    assertThat(result.systemMetadata()).containsEntry("resolvedIntent", "VIDEO");
  }

  @Test
  void intercept_recognizesVoiceKeyword() {
    PromptContext result = interceptor.intercept(createContext("Use voice to speak"));
    assertThat(result.systemMetadata()).containsEntry("resolvedIntent", "SPEECH");
  }

  @Test
  void intercept_recognizesPythonKeyword() {
    PromptContext result = interceptor.intercept(createContext("python script to sort"));
    assertThat(result.systemMetadata()).containsEntry("resolvedIntent", "CODE_SANDBOX");
  }
}
