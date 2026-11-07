package com.krizaka.sovkora.core.application.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PipelineUtilsTest {

  @Test
  void humanize_simpleWord_returnsUnchanged() {
    assertEquals("Hello", PipelineUtils.humanize("Hello"));
  }

  @Test
  void humanize_pascalCase_insertsSpaces() {
    assertEquals("User Context Resolver", PipelineUtils.humanize("UserContextResolver"));
  }

  @Test
  void humanize_singleCharacter_returnsUnchanged() {
    assertEquals("A", PipelineUtils.humanize("A"));
  }

  @Test
  void humanize_emptyString_returnsEmpty() {
    assertEquals("", PipelineUtils.humanize(""));
  }

  @Test
  void humanize_allUpperCase_returnsUnchanged() {
    assertEquals("ABC", PipelineUtils.humanize("ABC"));
  }

  @Test
  void humanize_consecutiveUpperCase_noSplitBetweenCaps() {
    assertEquals("MCPInterceptor", PipelineUtils.humanize("MCPInterceptor"));
  }

  @Test
  void humanize_lowerCase_returnsUnchanged() {
    assertEquals("hello", PipelineUtils.humanize("hello"));
  }
}
