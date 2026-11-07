package com.krizaka.sovkora.core.domain.model.chat;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.sovkora.core.domain.model.Context;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatRequestTest {

  private final Context ctx = new Context("user", "conv", Map.of(), Set.of());

  @Test
  void validConstruction() {
    var messages = List.of(new ChatRequest.ChatMessage("user", "hello"));
    var request = new ChatRequest("prompt", messages, Map.of("temp", 0.7), ctx);
    assertEquals("prompt", request.prompt());
    assertEquals(1, request.messages().size());
    assertEquals(0.7, request.settings().get("temp"));
    assertEquals(ctx, request.context());
  }

  @Test
  void nullMessages_defaultsToEmptyList() {
    var request = new ChatRequest("prompt", null, null, ctx);
    assertTrue(request.messages().isEmpty());
  }

  @Test
  void nullSettings_defaultsToEmptyMap() {
    var request = new ChatRequest("prompt", List.of(), null, ctx);
    assertTrue(request.settings().isEmpty());
  }

  @Test
  void nullPrompt_throws() {
    List<ChatRequest.ChatMessage> emptyList = List.of();
    assertThrows(NullPointerException.class, () -> new ChatRequest(null, emptyList, null, ctx));
  }

  @Test
  void blankPrompt_throws() {
    List<ChatRequest.ChatMessage> emptyList = List.of();
    assertThrows(IllegalArgumentException.class, () -> new ChatRequest("  ", emptyList, null, ctx));
  }

  @Test
  void nullContext_throws() {
    List<ChatRequest.ChatMessage> emptyList = List.of();
    assertThrows(
        NullPointerException.class, () -> new ChatRequest("prompt", emptyList, null, null));
  }

  @Test
  void simple_factory() {
    var request = ChatRequest.simple("hello");
    assertEquals("hello", request.prompt());
    assertTrue(request.messages().isEmpty());
    assertTrue(request.settings().isEmpty());
    assertEquals("anonymous", request.context().userId());
  }

  @Test
  void messages_defensiveCopy() {
    var request =
        new ChatRequest("prompt", List.of(new ChatRequest.ChatMessage("user", "hi")), null, ctx);
    var messages = request.messages();
    var newMessage = new ChatRequest.ChatMessage("assistant", "hello");
    assertThrows(UnsupportedOperationException.class, () -> messages.add(newMessage));
  }
}
