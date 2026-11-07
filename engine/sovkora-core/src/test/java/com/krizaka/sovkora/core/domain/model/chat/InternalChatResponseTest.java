package com.krizaka.sovkora.core.domain.model.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class InternalChatResponseTest {

  @Test
  void validConstruction() {
    var response = new InternalChatResponse("hello", "conv-1", Map.of("model", "gpt"));
    assertEquals("hello", response.content());
    assertEquals("conv-1", response.conversationId());
    assertEquals("gpt", response.metadata().get("model"));
  }

  @Test
  void nullContent_defaultsToEmptyString() {
    var response = new InternalChatResponse(null, null, null);
    assertEquals("", response.content());
    assertTrue(response.metadata().isEmpty());
  }

  @Test
  void metadata_isImmutable() {
    var response = new InternalChatResponse("hi", "conv", Map.of("key", "val"));
    var metadata = response.metadata();
    assertThrows(UnsupportedOperationException.class, () -> metadata.put("new", "val"));
  }
}
