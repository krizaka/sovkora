package com.krizaka.sovkora.core.domain.model.mcp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class McpToolInfoTest {

  @Test
  void validConstruction() {
    var tool = new McpToolInfo("search", "Search the web", "{\"query\":{\"type\":\"string\"}}");
    assertEquals("search", tool.name());
    assertEquals("Search the web", tool.description());
    assertEquals("{\"query\":{\"type\":\"string\"}}", tool.inputSchema());
  }

  @Test
  void nullName_throws() {
    assertThrows(NullPointerException.class, () -> new McpToolInfo(null, "desc", "schema"));
  }

  @Test
  void nullDescription_throws() {
    assertThrows(NullPointerException.class, () -> new McpToolInfo("name", null, "schema"));
  }

  @Test
  void nullInputSchema_throws() {
    assertThrows(NullPointerException.class, () -> new McpToolInfo("name", "desc", null));
  }
}
