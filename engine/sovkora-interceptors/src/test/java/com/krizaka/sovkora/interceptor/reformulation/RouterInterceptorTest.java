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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class RouterInterceptorTest {

  @Mock private CoreProperties properties;

  @Mock private ProviderChatClient chatClient;

  private Resource routerSystemResource;

  @BeforeEach
  void setUp() {
    routerSystemResource = new ByteArrayResource("system prompt".getBytes());
  }

  private RouterInterceptor interceptor() {
    return new RouterInterceptor(chatClient, properties, routerSystemResource);
  }

  private void routerConfigured(String provider, String model) {
    var routerConfig = mock(CoreProperties.InterceptorConfig.class);
    lenient().when(routerConfig.provider()).thenReturn(provider);
    lenient().when(routerConfig.model()).thenReturn(model);
    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.router()).thenReturn(routerConfig);
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
    routerConfigured("nonexistent", null);
    when(chatClient.resolve("nonexistent")).thenReturn(Optional.empty());

    var context = new PromptContext("test query", Map.of());

    assertSame(context, interceptor().intercept(context));
    verify(chatClient, never()).complete(any(), any(), any(), any(), any());
  }

  @Test
  void constructor_nullClient_throwsNpe() {
    assertThrows(
        NullPointerException.class,
        () -> new RouterInterceptor(null, properties, routerSystemResource));
  }

  @Test
  void constructor_nullProperties_throwsNpe() {
    assertThrows(
        NullPointerException.class,
        () -> new RouterInterceptor(chatClient, null, routerSystemResource));
  }

  @Test
  void intercept_whenEnabledAndRoutesSuccessfully_returnsContextWithRoutedProvider() {
    routerConfigured("ollama", "llama3");
    when(chatClient.resolve("ollama")).thenReturn(Optional.of("ollama"));
    when(chatClient.providers()).thenReturn(Set.of("ollama", "openai"));
    when(chatClient.complete(eq("ollama"), eq("ollama"), any(), eq("llama3"), eq(0.0)))
        .thenReturn(" ollama\n");

    var context = new PromptContext("test query", Map.of()).withRefinedPrompt("refined query");
    PromptContext result = interceptor().intercept(context);

    assertEquals("ollama", result.routedProvider());
  }

  @Test
  void intercept_asksWithTheSystemPromptThenTheRefinedQueryAndTheKeys() {
    routerConfigured("ollama", "llama3");
    when(chatClient.resolve("ollama")).thenReturn(Optional.of("ollama"));
    when(chatClient.providers()).thenReturn(Set.of("openai", "ollama"));
    when(chatClient.complete(any(), any(), any(), any(), any())).thenReturn("ollama");

    interceptor().intercept(new PromptContext("q", Map.of()).withRefinedPrompt("refined query"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<EngineMessage>> messages = ArgumentCaptor.forClass(List.class);
    verify(chatClient).complete(any(), any(), messages.capture(), any(), any());
    assertEquals(EngineMessage.system("system prompt"), messages.getValue().getFirst());
    assertTrue(messages.getValue().get(1).text().contains("refined query"));
    assertTrue(messages.getValue().get(1).text().contains("ollama, openai"));
  }

  @Test
  void intercept_onATurnCarryingMedia_leavesTheProviderToTheCaller() {
    // ADR-063: the router reads text and cannot tell which provider can see an image, and a routed
    // provider outranks the one the caller chose — a vision job naming its vision model would be
    // sent wherever the text reads best.
    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.router()).thenReturn(mock(CoreProperties.InterceptorConfig.class));
    when(properties.orchestration()).thenReturn(orchestration);

    var context =
        new PromptContext(
            "Analyze this image", Map.of(), Map.of(), "Analyze this image", null, null, 1);
    PromptContext result = interceptor().intercept(context);

    assertSame(context, result);
    assertNull(result.routedProvider());
    verifyNoInteractions(chatClient);
  }

  @Test
  void intercept_withPartialMatchProvider_resolvesAndRoutesCorrectly() {
    routerConfigured("open", "gpt-4");
    when(chatClient.resolve("open")).thenReturn(Optional.of("openai"));
    when(chatClient.providers()).thenReturn(Set.of("openai"));
    // The model asked is the resolved one; the options are built for the configured name.
    when(chatClient.complete(eq("openai"), eq("open"), any(), eq("gpt-4"), eq(0.0)))
        .thenReturn("openai");

    var context = new PromptContext("test query", Map.of()).withRefinedPrompt("refined query");
    PromptContext result = interceptor().intercept(context);

    assertEquals("openai", result.routedProvider());
  }

  @Test
  void intercept_whenTheAnswerIsNotARegisteredProvider_returnsUnmodifiedContext() {
    routerConfigured("ollama", "llama3");
    when(chatClient.resolve("ollama")).thenReturn(Optional.of("ollama"));
    when(chatClient.providers()).thenReturn(Set.of("ollama"));
    when(chatClient.complete(any(), any(), any(), any(), any())).thenReturn("somewhere else");

    var context = new PromptContext("test query", Map.of()).withRefinedPrompt("refined query");

    assertSame(context, interceptor().intercept(context));
  }

  @Test
  void intercept_whenChatModelThrowsException_returnsUnmodifiedContext() {
    routerConfigured("ollama", "llama3");
    when(chatClient.resolve("ollama")).thenReturn(Optional.of("ollama"));
    when(chatClient.providers()).thenReturn(Set.of("ollama"));
    when(chatClient.complete(any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("LLM failure"));

    var context = new PromptContext("test query", Map.of()).withRefinedPrompt("refined query");

    assertSame(context, interceptor().intercept(context));
  }
}
