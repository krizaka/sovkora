package com.krizaka.sovkora.core.domain.model.chat;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.sovkora.core.domain.model.Context;
import java.util.List;
import org.junit.jupiter.api.Test;

class InternalChatRequestTest {

  @Test
  void validConstruction() {
    var msg = new InternalChatRequest.ChatMessage("user", "hello");
    var request = new InternalChatRequest("prompt", List.of(msg), null, Context.anonymous());
    assertEquals("prompt", request.prompt());
    assertEquals(1, request.messages().size());
    assertFalse(request.streaming());
  }

  @Test
  void streamingConstruction() {
    var request = new InternalChatRequest("prompt", null, null, Context.anonymous(), true);
    assertTrue(request.streaming());
  }

  @Test
  void nullMessages_defaultsToEmptyList() {
    var request = new InternalChatRequest("prompt", null, null, Context.anonymous());
    assertTrue(request.messages().isEmpty());
  }

  @Test
  void simple_factory() {
    var request = InternalChatRequest.simple("hello");
    assertEquals("hello", request.prompt());
    assertTrue(request.messages().isEmpty());
    assertNull(request.options());
    assertEquals("anonymous", request.context().userId());
  }

  @Test
  void chatMessage_nullRole_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> new InternalChatRequest.ChatMessage(null, "content"));
  }

  @Test
  void chatMessage_blankRole_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> new InternalChatRequest.ChatMessage("  ", "content"));
  }

  @Test
  void chatMessage_nullContent_defaultsToEmpty() {
    var msg = new InternalChatRequest.ChatMessage("user", null);
    assertEquals("", msg.content());
  }

  @Test
  void compileMessages_addsRefinedPrompt() {
    var msg = new InternalChatRequest.ChatMessage("user", "hello");
    var request = new InternalChatRequest("prompt", List.of(msg), null, Context.anonymous());
    List<EngineMessage> compiled =
        request.compileMessages("refined prompt", cm -> EngineMessage.user(cm.content()));
    assertEquals(2, compiled.size());
  }

  @Test
  void compileMessages_blankRefinedPrompt_skipped() {
    var request = new InternalChatRequest("prompt", List.of(), null, Context.anonymous());
    List<EngineMessage> compiled =
        request.compileMessages("  ", cm -> EngineMessage.user(cm.content()));
    assertTrue(compiled.isEmpty());
  }
}
