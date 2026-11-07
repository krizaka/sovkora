package com.krizaka.sovkora.interceptor.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DynamicMemoryCondenserTest {

  private final DynamicMemoryCondenser condenser = new DynamicMemoryCondenser();

  private PromptContext createContext(Map<String, Object> systemMetadata) {
    return new PromptContext("test query", Map.of(), systemMetadata, "test query", null, null);
  }

  @Test
  void intercept_passesThrough_whenNoConversationHistory() {
    PromptContext ctx = createContext(Map.of());
    PromptContext result = condenser.intercept(ctx);
    assertThat(result.systemMetadata()).doesNotContainKey("condensedSessionFacts");
  }

  @Test
  void intercept_passesThrough_whenHistoryIsNotAList() {
    PromptContext ctx = createContext(Map.of("conversationHistory", "not-a-list"));
    PromptContext result = condenser.intercept(ctx);
    assertThat(result.systemMetadata()).doesNotContainKey("condensedSessionFacts");
  }

  @Test
  void intercept_passesThrough_whenHistoryIsEmpty() {
    PromptContext ctx = createContext(Map.of("conversationHistory", List.of()));
    PromptContext result = condenser.intercept(ctx);
    assertThat(result.systemMetadata()).doesNotContainKey("condensedSessionFacts");
  }

  @Test
  void intercept_preservesRecentTurns_whenHistoryIsSmall() {
    var history = List.of("turn1", "turn2", "turn3");
    PromptContext ctx = createContext(Map.of("conversationHistory", history));
    PromptContext result = condenser.intercept(ctx);

    assertThat(result.systemMetadata()).containsKey("rawRecentTurns");
    @SuppressWarnings("unchecked")
    List<String> rawTurns = (List<String>) result.systemMetadata().get("rawRecentTurns");
    assertThat(rawTurns).hasSize(3);
    assertThat(result.systemMetadata()).doesNotContainKey("condensedSessionFacts");
  }

  @Test
  void intercept_condensesOlderTurns_whenHistoryExceedsWindow() {
    var history = List.of("turn1", "turn2", "turn3", "turn4", "turn5", "turn6");
    var sysMap = new HashMap<String, Object>();
    sysMap.put("conversationHistory", history);
    PromptContext ctx = createContext(Map.copyOf(sysMap));
    PromptContext result = condenser.intercept(ctx);

    assertThat(result.systemMetadata()).containsKey("condensedSessionFacts");
    String facts = (String) result.systemMetadata().get("condensedSessionFacts");
    assertThat(facts)
        .startsWith("SESSION_FACTS:")
        .contains("[turn1]")
        .contains("[turn2]")
        .contains("[turn3]");

    @SuppressWarnings("unchecked")
    List<String> rawTurns = (List<String>) result.systemMetadata().get("rawRecentTurns");
    assertThat(rawTurns).hasSize(3).containsExactly("turn4", "turn5", "turn6");
  }

  @Test
  void intercept_truncatesLongTurns() {
    String longTurn = "x".repeat(300);
    var history = List.of(longTurn, "a", "b", "c", "d");
    var sysMap = new HashMap<String, Object>();
    sysMap.put("conversationHistory", history);
    PromptContext ctx = createContext(Map.copyOf(sysMap));
    PromptContext result = condenser.intercept(ctx);

    String facts = (String) result.systemMetadata().get("condensedSessionFacts");
    assertThat(facts).contains("...").doesNotContain("x".repeat(300));
  }
}
