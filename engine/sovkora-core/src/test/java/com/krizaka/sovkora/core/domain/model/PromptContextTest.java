package com.krizaka.sovkora.core.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptContextTest {

  @Test
  void fullConstruction_setsAllFields() {
    var ctx =
        new PromptContext(
            "query",
            Map.of("key", "value"),
            Map.of("sys", "val"),
            "refined",
            "openai",
            RoutingMode.AGENTIC);
    assertEquals("query", ctx.rawUserQuery());
    assertEquals(Map.of("key", "value"), ctx.userMetadata());
    assertEquals(Map.of("sys", "val"), ctx.systemMetadata());
    assertEquals("refined", ctx.refinedPrompt());
    assertEquals("openai", ctx.routedProvider());
    assertEquals(RoutingMode.AGENTIC, ctx.routingMode());
  }

  @Test
  void shortConstruction_setsDefaults() {
    var ctx = new PromptContext("query", Map.of("key", "value"));
    assertEquals("query", ctx.rawUserQuery());
    assertEquals("query", ctx.refinedPrompt());
    assertNull(ctx.routedProvider());
    assertEquals(RoutingMode.DETERMINISTIC, ctx.routingMode());
    assertTrue(ctx.systemMetadata().isEmpty());
  }

  @Test
  @DisplayName("[ADR-063] a text-only context carries no media")
  void textOnlyConstruction_carriesNoMedia() {
    assertFalse(new PromptContext("query", Map.of()).carriesMedia());
    assertEquals(
        0, new PromptContext("query", Map.of(), Map.of(), "refined", null, null).mediaParts());
  }

  @Test
  @DisplayName("[ADR-063] the media declaration survives every copy, map replacements included")
  void mediaParts_survivesEveryWither() {
    var ctx = new PromptContext("query", Map.of(), Map.of(), "query", null, null, 1);
    var copied =
        ctx.withRefinedPrompt("refined")
            .withRoutedProvider("ollama")
            .withRoutingMode(RoutingMode.AGENTIC)
            .withUserMetadata(Map.of("u", "v"))
            .withSystemMetadata(Map.of("s", "v"))
            .withBillingHold("hold-1");
    assertEquals(1, copied.mediaParts());
    assertTrue(copied.carriesMedia());
  }

  @Test
  void negativeMediaParts_isRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PromptContext("query", Map.of(), Map.of(), "query", null, null, -1));
  }

  @Test
  void nullRoutingMode_defaultsToDeterministic() {
    var ctx = new PromptContext("query", Map.of(), Map.of(), "refined", null, null);
    assertEquals(RoutingMode.DETERMINISTIC, ctx.routingMode());
  }

  @Test
  void nullMetadata_defaultsToEmptyMap() {
    var ctx = new PromptContext("query", null, null, "refined", null, null);
    assertTrue(ctx.userMetadata().isEmpty());
    assertTrue(ctx.systemMetadata().isEmpty());
  }

  @Test
  void sanitizeMap_removesNullValues() {
    var input = new HashMap<String, Object>();
    input.put("key", "value");
    input.put("nullValue", null);
    var ctx = new PromptContext("query", input);
    assertEquals(1, ctx.userMetadata().size());
    assertEquals("value", ctx.userMetadata().get("key"));
  }

  @Test
  void withRefinedPrompt_returnsNewInstance() {
    var ctx = new PromptContext("query", Map.of());
    var refined = ctx.withRefinedPrompt("new prompt");
    assertEquals("new prompt", refined.refinedPrompt());
    assertEquals("query", refined.rawUserQuery());
  }

  @Test
  void withRoutedProvider_returnsNewInstance() {
    var ctx = new PromptContext("query", Map.of());
    var routed = ctx.withRoutedProvider("openai");
    assertEquals("openai", routed.routedProvider());
  }

  @Test
  void withRoutingMode_returnsNewInstance() {
    var ctx = new PromptContext("query", Map.of());
    var agentic = ctx.withRoutingMode(RoutingMode.AGENTIC);
    assertEquals(RoutingMode.AGENTIC, agentic.routingMode());
  }

  @Test
  void withUserMetadata_returnsNewInstance() {
    var ctx = new PromptContext("query", Map.of());
    var updated = ctx.withUserMetadata(Map.of("new", "data"));
    assertEquals(Map.of("new", "data"), updated.userMetadata());
  }

  @Test
  void withSystemMetadata_returnsNewInstance() {
    var ctx = new PromptContext("query", Map.of());
    var updated = ctx.withSystemMetadata(Map.of("sys", "data"));
    assertEquals(Map.of("sys", "data"), updated.systemMetadata());
  }

  @Test
  void metadata_isImmutable() {
    var ctx = new PromptContext("query", Map.of("k", "v"));
    var userMeta = ctx.userMetadata();
    assertThrows(UnsupportedOperationException.class, () -> userMeta.put("new", "val"));
    var sysMeta = ctx.systemMetadata();
    assertThrows(UnsupportedOperationException.class, () -> sysMeta.put("new", "val"));
  }

  @Test
  @DisplayName("the credit reservation survives the gap between the gate and settlement")
  void carriesTheBillingHold() {
    PromptContext gated = new PromptContext("hello", Map.of()).withBillingHold("hold-7");

    assertEquals("hold-7", gated.billingHoldId());
  }

  @Test
  @DisplayName("an unmetered turn reports no hold rather than an empty string")
  void reportsNoHoldWhenUnmetered() {
    assertNull(new PromptContext("hello", Map.of()).billingHoldId());
  }

  @Test
  @DisplayName("stamping the hold preserves everything else the pipeline had enriched")
  void preservesTheRestOfTheContext() {
    PromptContext enriched =
        new PromptContext("hello", Map.of("userId", "actor-1"))
            .withSystemMetadata(Map.of("tools", "mcp"))
            .withRefinedPrompt("refined")
            .withBillingHold("hold-8");

    assertEquals("refined", enriched.refinedPrompt());
    assertEquals("actor-1", enriched.userMetadata().get("userId"));
    assertEquals("mcp", enriched.systemMetadata().get("tools"));
    assertEquals("hold-8", enriched.billingHoldId());
  }
}
