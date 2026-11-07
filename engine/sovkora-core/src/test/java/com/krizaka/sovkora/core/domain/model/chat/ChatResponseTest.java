package com.krizaka.sovkora.core.domain.model.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatResponseTest {

  @Test
  void validConstruction() {
    var response =
        new ChatResponse(
            "Hello!", "conv-1", TokenUsage.reported(120, 80, 200), Map.of("provider", "openai"));
    assertEquals("Hello!", response.content());
    assertEquals("conv-1", response.conversationId());
    assertEquals("openai", response.metadata().get("provider"));
    assertEquals(200, response.tokenUsage().totalTokens());
  }

  @Test
  void nullFields_allowed() {
    var response = new ChatResponse(null, null, null, null);
    assertNull(response.content());
    assertNull(response.conversationId());
    assertNull(response.metadata());
  }

  @Test
  @DisplayName("[ADR-041] usage is never null — unreported is a fact, not a missing value")
  void tokenUsageDefaultsToUnreported() {
    var response = new ChatResponse("Hello!", "conv-1", null, Map.of());

    assertNotNull(response.tokenUsage(), "a null here would be indistinguishable from zero tokens");
    assertFalse(response.tokenUsage().reported());
  }

  @Test
  @DisplayName("An unmeasured path says so explicitly rather than passing a bare null")
  void unmeasuredFactoryReportsNothing() {
    var response = ChatResponse.unmeasured("Hello!", "conv-1", Map.of());

    assertFalse(response.tokenUsage().reported());
    assertEquals("Hello!", response.content());
  }

  @Test
  @DisplayName("Reported usage crosses the port, which is what makes an async turn billable")
  void reportedUsageSurvivesTheBoundary() {
    var response = new ChatResponse("Hi", "conv-1", TokenUsage.reported(400, 3000, 3400), Map.of());

    assertTrue(response.tokenUsage().reported());
    assertEquals(3400, response.tokenUsage().totalTokens());
  }
}
