package com.krizaka.sovkora.interceptor.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.EngineChatOptions;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.ports.outbound.ConversationMemory;
import com.krizaka.sovkora.core.domain.ports.outbound.MemoryResolver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MemoryInterceptorTest {

  @Test
  @DisplayName("preProcess injects memory history before current prompt")
  void preProcessInjectsHistory() {
    MemoryResolver resolver = mock(MemoryResolver.class);
    ConversationMemory chatMemory = mock(ConversationMemory.class);
    when(resolver.resolve("conv-123")).thenReturn(chatMemory);

    List<EngineMessage> history =
        List.of(EngineMessage.user("Hi"), EngineMessage.assistant("Hello"));
    when(chatMemory.get("conv-123")).thenReturn(history);

    MemoryInterceptor interceptor = new MemoryInterceptor(resolver);

    InternalChatRequest request = mock(InternalChatRequest.class);
    Context context = mock(Context.class);
    when(request.context()).thenReturn(context);
    when(context.conversationId()).thenReturn("conv-123");

    List<EngineMessage> messages = new ArrayList<>(List.of(EngineMessage.user("Current query")));
    EngineChatOptions options = new EngineChatOptions(null, null, null);

    interceptor.preProcess(request, "Current query", messages, options);

    assertThat(messages).hasSize(3);
    assertThat(messages.getFirst().text()).isEqualTo("Hi");
    assertThat(messages.get(1).text()).isEqualTo("Hello");
    assertThat(messages.get(2).text()).isEqualTo("Current query");
  }

  @Test
  @DisplayName("postProcess appends query and response to ConversationMemory")
  void postProcessAppendsToMemory() {
    MemoryResolver resolver = mock(MemoryResolver.class);
    ConversationMemory chatMemory = mock(ConversationMemory.class);
    when(resolver.resolve("conv-123")).thenReturn(chatMemory);

    MemoryInterceptor interceptor = new MemoryInterceptor(resolver);

    InternalChatRequest request = mock(InternalChatRequest.class);
    Context context = mock(Context.class);
    when(request.context()).thenReturn(context);
    when(context.conversationId()).thenReturn("conv-123");

    interceptor.postProcess(request, "User Prompt", "AI Response");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<EngineMessage>> captor = ArgumentCaptor.forClass(List.class);
    verify(chatMemory).add(eq("conv-123"), captor.capture());

    List<EngineMessage> added = captor.getValue();
    assertThat(added).hasSize(2);
    assertThat(added.getFirst()).extracting(EngineMessage::role).isEqualTo(EngineMessage.Role.USER);
    assertThat(added.getFirst().text()).isEqualTo("User Prompt");
    assertThat(added.get(1))
        .extracting(EngineMessage::role)
        .isEqualTo(EngineMessage.Role.ASSISTANT);
    assertThat(added.get(1).text()).isEqualTo("AI Response");
  }

  private static Context retained(String retention) {
    java.util.Map<String, Object> platform = new java.util.HashMap<>();
    if (retention != null) {
      platform.put(Context.RETENTION_KEY, retention);
    }
    return new Context("user-1", "conv-9", platform, java.util.Set.of());
  }

  @Test
  @DisplayName("retention none: what the person said and what was answered are never written")
  void retentionNoneWritesNothing() {
    MemoryResolver resolver = mock(MemoryResolver.class);
    MemoryInterceptor interceptor = new MemoryInterceptor(resolver);
    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.context()).thenReturn(retained(Context.RETENTION_NONE));

    interceptor.postProcess(request, "a private sentence", "a private answer");

    verifyNoInteractions(resolver);
  }

  @Test
  @DisplayName("retention none: no stored history is read back into the prompt either")
  void retentionNoneReadsNothing() {
    MemoryResolver resolver = mock(MemoryResolver.class);
    MemoryInterceptor interceptor = new MemoryInterceptor(resolver);
    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.context()).thenReturn(retained(Context.RETENTION_NONE));
    List<EngineMessage> messages = new ArrayList<>(List.of(EngineMessage.user("Current query")));

    interceptor.preProcess(
        request, "Current query", messages, new EngineChatOptions(null, null, null));

    assertThat(messages).hasSize(1);
    verifyNoInteractions(resolver);
  }

  @Test
  @DisplayName("any other retention value keeps the default: the turn is remembered")
  void otherRetentionKeepsDefault() {
    MemoryResolver resolver = mock(MemoryResolver.class);
    ConversationMemory chatMemory = mock(ConversationMemory.class);
    when(resolver.resolve("conv-9")).thenReturn(chatMemory);
    MemoryInterceptor interceptor = new MemoryInterceptor(resolver);
    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.context()).thenReturn(retained("standard"));

    interceptor.postProcess(request, "q", "a");

    verify(chatMemory).add(eq("conv-9"), anyList());
  }
}
