package com.krizaka.sovkora.interceptor.reformulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.PromptContext;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class RefinerInterceptorTest {

  @Mock private CoreProperties properties;

  @Mock private ProviderChatClient chatClient;

  private Resource systemResource;
  private Resource contextResource;

  @BeforeEach
  void setUp() {
    systemResource = new ByteArrayResource("system prompt".getBytes());
    contextResource = new ByteArrayResource("query={rawQuery} user={userMetadata}".getBytes());
  }

  private RefinerInterceptor interceptor() {
    return new RefinerInterceptor(chatClient, properties, systemResource, contextResource);
  }

  private void refinerConfigured(String provider, String model, Double temperature) {
    var refinerConfig = mock(CoreProperties.InterceptorConfig.class);
    lenient().when(refinerConfig.provider()).thenReturn(provider);
    lenient().when(refinerConfig.model()).thenReturn(model);
    lenient().when(refinerConfig.temperature()).thenReturn(temperature);
    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.refiner()).thenReturn(refinerConfig);
    when(properties.orchestration()).thenReturn(orchestration);
  }

  @Test
  void isAiDependent_returnsTrue() {
    assertTrue(interceptor().isAiDependent());
  }

  @Test
  void intercept_whenDisabled_returnsContextUnchanged() {
    when(properties.orchestration()).thenReturn(null);

    var context = new PromptContext("test query", Map.of());

    assertSame(context, interceptor().intercept(context));
  }

  @Test
  void intercept_withNoRegisteredModels_returnsContextUnchanged() {
    refinerConfigured("nonexistent", null, null);
    when(chatClient.resolve("nonexistent")).thenReturn(Optional.empty());

    var context = new PromptContext("test query", Map.of());

    assertSame(context, interceptor().intercept(context));
    verify(chatClient, never()).complete(any(), any(), any(), any(), any());
  }

  @Test
  void constructor_nullClient_throwsNpe() {
    assertThrows(
        NullPointerException.class,
        () -> new RefinerInterceptor(null, properties, systemResource, contextResource));
  }

  @Test
  void constructor_nullProperties_throwsNpe() {
    assertThrows(
        NullPointerException.class,
        () -> new RefinerInterceptor(chatClient, null, systemResource, contextResource));
  }

  @Test
  void constructor_nullSystemResource_throwsNpe() {
    assertThrows(
        NullPointerException.class,
        () -> new RefinerInterceptor(chatClient, properties, null, contextResource));
  }

  @Test
  void constructor_nullContextResource_throwsNpe() {
    assertThrows(
        NullPointerException.class,
        () -> new RefinerInterceptor(chatClient, properties, systemResource, null));
  }

  @Test
  void intercept_whenEnabledAndRefinesSuccessfully_returnsContextWithRefinedPrompt() {
    refinerConfigured("ollama", "llama3", 0.7);
    when(chatClient.resolve("ollama")).thenReturn(Optional.of("ollama"));
    when(chatClient.complete(eq("ollama"), eq("ollama"), any(), eq("llama3"), eq(0.7)))
        .thenReturn("  highly refined query ");

    var context = new PromptContext("test query", Map.of());
    PromptContext result = interceptor().intercept(context);

    assertEquals("highly refined query", result.refinedPrompt());
  }

  @Test
  void intercept_asksWithTheSystemPromptThenTheFilledEnvelope() {
    refinerConfigured("ollama", "llama3", 0.7);
    when(chatClient.resolve("ollama")).thenReturn(Optional.of("ollama"));
    when(chatClient.complete(any(), any(), any(), any(), any())).thenReturn("refined");

    interceptor().intercept(new PromptContext("test query", Map.of()));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<EngineMessage>> messages = ArgumentCaptor.forClass(List.class);
    verify(chatClient).complete(any(), any(), messages.capture(), any(), any());
    assertEquals(EngineMessage.system("system prompt"), messages.getValue().getFirst());
    assertEquals("query=test query user={}", messages.getValue().get(1).text());
  }

  @Test
  void intercept_onATurnCarryingMedia_keepsTheUsersWords() {
    // ADR-063: the refinement envelope never carries the image. A rewrite of "t'en penses quoi ?"
    // made without the photograph is what the vision model would receive beside it.
    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.refiner()).thenReturn(mock(CoreProperties.InterceptorConfig.class));
    when(properties.orchestration()).thenReturn(orchestration);

    var context =
        new PromptContext(
            "t'en penses quoi ?", Map.of(), Map.of(), "t'en penses quoi ?", null, null, 1);
    PromptContext result = interceptor().intercept(context);

    assertSame(context, result);
    assertEquals("t'en penses quoi ?", result.refinedPrompt());
    verifyNoInteractions(chatClient);
  }

  @Test
  void intercept_withPartialMatchProvider_resolvesAndRefinesCorrectly() {
    refinerConfigured("open", "gpt-4", 0.5);
    when(chatClient.resolve("open")).thenReturn(Optional.of("openai"));
    when(chatClient.complete(eq("openai"), eq("open"), any(), eq("gpt-4"), eq(0.5)))
        .thenReturn("highly refined query partial");

    var context = new PromptContext("test query", Map.of());
    PromptContext result = interceptor().intercept(context);

    assertEquals("highly refined query partial", result.refinedPrompt());
  }

  @Test
  void intercept_whenTheModelSaysNothing_returnsUnmodifiedContext() {
    refinerConfigured("ollama", "llama3", 0.7);
    when(chatClient.resolve("ollama")).thenReturn(Optional.of("ollama"));
    when(chatClient.complete(any(), any(), any(), any(), any())).thenReturn("   ");

    var context = new PromptContext("test query", Map.of());

    assertSame(context, interceptor().intercept(context));
  }

  @Test
  void intercept_whenChatModelThrowsException_returnsUnmodifiedContext() {
    refinerConfigured("ollama", "llama3", 0.7);
    when(chatClient.resolve("ollama")).thenReturn(Optional.of("ollama"));
    when(chatClient.complete(any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("LLM failure"));

    var context = new PromptContext("test query", Map.of());

    assertSame(context, interceptor().intercept(context));
  }
}
