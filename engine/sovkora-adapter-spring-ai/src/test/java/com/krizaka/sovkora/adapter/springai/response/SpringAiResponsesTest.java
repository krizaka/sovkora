package com.krizaka.sovkora.adapter.springai.response;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class SpringAiResponsesTest {

  @Test
  void extractResponseText_nullResponse_returnsEmpty() {
    Optional<String> result = SpringAiResponses.extractResponseText(null);
    assertTrue(result.isEmpty());
  }

  @Test
  void extractResponseText_nullResult_returnsEmpty() {
    ChatResponse response = new ChatResponse(java.util.List.of());
    Optional<String> result = SpringAiResponses.extractResponseText(response);
    assertTrue(result.isEmpty());
  }

  @Test
  void extractResponseText_validResponse_returnsTrimmedText() {
    Generation generation = new Generation(new AssistantMessage("  refined prompt  "));
    ChatResponse response = new ChatResponse(java.util.List.of(generation));
    Optional<String> result = SpringAiResponses.extractResponseText(response);
    assertTrue(result.isPresent());
    assertEquals("refined prompt", result.get());
  }

  @Test
  void extractResponseText_blankText_returnsEmpty() {
    Generation generation = new Generation(new AssistantMessage("   "));
    ChatResponse response = new ChatResponse(java.util.List.of(generation));
    Optional<String> result = SpringAiResponses.extractResponseText(response);
    assertTrue(result.isEmpty());
  }

  @Test
  void extractResponseText_emptyText_returnsEmpty() {
    Generation generation = new Generation(new AssistantMessage(""));
    ChatResponse response = new ChatResponse(java.util.List.of(generation));
    Optional<String> result = SpringAiResponses.extractResponseText(response);
    assertTrue(result.isEmpty());
  }
}
