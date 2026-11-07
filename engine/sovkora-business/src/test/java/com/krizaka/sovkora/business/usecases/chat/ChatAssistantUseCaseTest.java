package com.krizaka.sovkora.business.usecases.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.business.api.Capability;
import com.krizaka.sovkora.business.api.ChatPayload;
import com.krizaka.sovkora.business.api.UseCaseContext;
import com.krizaka.sovkora.business.prompt.MarkdownPromptResolver;
import com.krizaka.sovkora.core.domain.model.chat.ChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.ChatResponse;
import com.krizaka.sovkora.core.domain.ports.inbound.AiClient;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatAssistantUseCaseTest {

  private final MarkdownPromptResolver prompts = mock(MarkdownPromptResolver.class);

  @Test
  void descriptor_isChatCapabilityWithPersona() {
    var useCase = new ChatAssistantUseCase(mock(AiClient.class), prompts);
    assertEquals("chat.assistant", useCase.descriptor().id());
    assertEquals(Capability.CHAT, useCase.descriptor().capability());
    assertTrue(useCase.descriptor().personas().contains("personas/default-assistant"));
  }

  @Test
  void execute_resolvesPersona_buildsChatRequest_andDelegatesToAiClient() {
    AiClient aiClient = mock(AiClient.class);
    when(prompts.resolve("personas/default-assistant")).thenReturn(Optional.of("BE CONCISE"));
    when(aiClient.chat(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("answer", "session-1", Map.of()));

    var useCase = new ChatAssistantUseCase(aiClient, prompts);
    var ctx =
        new UseCaseContext("intent-1", "actor-1", "session-1", Set.of(), Map.of("language", "en"));
    ChatResponse response = useCase.execute(ctx, new ChatPayload("hello", null));

    assertEquals("answer", response.content());
    ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
    verify(aiClient).chat(captor.capture());
    ChatRequest sent = captor.getValue();
    assertEquals("hello", sent.prompt());
    assertEquals("actor-1", sent.context().userId());
    assertEquals("session-1", sent.context().conversationId());
    assertEquals(1, sent.messages().size());
    assertEquals("system", sent.messages().getFirst().role());
    assertEquals("BE CONCISE", sent.messages().getFirst().content());
  }

  @Test
  void execute_missingPersona_sendsNoSystemMessage() {
    AiClient aiClient = mock(AiClient.class);
    when(prompts.resolve(any())).thenReturn(Optional.empty());
    when(aiClient.chat(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("answer", "s", Map.of()));

    var useCase = new ChatAssistantUseCase(aiClient, prompts);
    useCase.execute(
        new UseCaseContext("i", "a", "s", Set.of(), Map.of()), new ChatPayload("hi", null));

    ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
    verify(aiClient).chat(captor.capture());
    assertTrue(captor.getValue().messages().isEmpty());
  }

  @Test
  void constructor_nullDependencies_throw() {
    assertThrows(NullPointerException.class, () -> new ChatAssistantUseCase(null, prompts));
    assertThrows(
        NullPointerException.class, () -> new ChatAssistantUseCase(mock(AiClient.class), null));
  }
}
