package com.krizaka.sovkora.adapter.springai.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.ports.outbound.ToolResultCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

@ExtendWith(MockitoExtension.class)
class CachingToolCallbackTest {

  @Mock private ToolCallback delegate;

  @Mock private ToolResultCache cacheService;

  @Mock private ToolDefinition toolDefinition;

  @BeforeEach
  void setUp() {
    lenient().when(delegate.getToolDefinition()).thenReturn(toolDefinition);
    lenient().when(toolDefinition.name()).thenReturn("testTool");
  }

  @Test
  void getToolDefinition_delegatesToWrapped() {
    var callback = new CachingToolCallback(delegate, cacheService, true, 300, "scope-a");
    assertSame(toolDefinition, callback.getToolDefinition());
  }

  @Test
  void call_cacheDisabled_delegatesDirectly() {
    var callback = new CachingToolCallback(delegate, cacheService, false, 300, "scope-a");
    when(delegate.call("{\"key\":\"value\"}")).thenReturn("result");

    String result = callback.call("{\"key\":\"value\"}");

    assertEquals("result", result);
    verify(cacheService, never()).get(any(), any());
    verify(cacheService, never()).put(any(), any(), any(), anyLong());
  }

  @Test
  void call_cacheEnabled_hitReturnsFromCache() {
    var callback = new CachingToolCallback(delegate, cacheService, true, 300, "scope-a");
    when(cacheService.get("testTool", "scope-a\n{\"key\":\"value\"}")).thenReturn("cached-result");

    String result = callback.call("{\"key\":\"value\"}");

    assertEquals("cached-result", result);
    verify(delegate, never()).call(any());
  }

  @Test
  void call_cacheEnabled_missExecutesAndCaches() {
    var callback = new CachingToolCallback(delegate, cacheService, true, 300, "scope-a");
    when(cacheService.get("testTool", "scope-a\n{\"key\":\"value\"}")).thenReturn(null);
    when(delegate.call("{\"key\":\"value\"}")).thenReturn("fresh-result");

    String result = callback.call("{\"key\":\"value\"}");

    assertEquals("fresh-result", result);
    verify(cacheService).put("testTool", "scope-a\n{\"key\":\"value\"}", "fresh-result", 300);
  }

  @Test
  void call_anotherScopeNeverReadsTheEntryOfThisOne() {
    var other = new CachingToolCallback(delegate, cacheService, true, 300, "scope-b");
    when(cacheService.get("testTool", "scope-b\n{\"key\":\"value\"}")).thenReturn(null);
    when(delegate.call("{\"key\":\"value\"}")).thenReturn("fresh-for-b");

    assertEquals("fresh-for-b", other.call("{\"key\":\"value\"}"));
    verify(cacheService, never()).get("testTool", "scope-a\n{\"key\":\"value\"}");
  }
}
