package com.krizaka.sovkora.core.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.mcp.McpToolInfo;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException.Reason;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.AccessAudit;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultMcpServiceTest {

  private static final Actor USER = Actor.of("user-1", List.of("ROLE_USER")).orElseThrow();

  private final ToolRegistry toolRegistry = mock(ToolRegistry.class);
  private final AccessAudit audit = mock(AccessAudit.class);
  private final AtomicInteger runs = new AtomicInteger();

  private DefaultMcpService mcpService;

  private EngineTool testTool() {
    return new EngineTool(
        "testTool",
        "A test tool description",
        "{\"type\":\"object\"}",
        json -> {
          runs.incrementAndGet();
          return "{\"arg\":\"val\"}".equals(json) ? "execution result" : "unexpected " + json;
        },
        null);
  }

  @BeforeEach
  void setUp() {
    mcpService = new DefaultMcpService(toolRegistry, audit);
  }

  @Test
  void shouldReturnAvailableTools() {
    when(toolRegistry.getRegisteredTools(USER)).thenReturn(List.of(testTool()));

    List<McpToolInfo> tools = mcpService.getAvailableTools(USER);

    assertThat(tools).hasSize(1);
    McpToolInfo toolInfo = tools.getFirst();
    assertThat(toolInfo.name()).isEqualTo("testTool");
    assertThat(toolInfo.description()).isEqualTo("A test tool description");
    assertThat(toolInfo.inputSchema()).isEqualTo("{\"type\":\"object\"}");
  }

  @Test
  void shouldExecuteToolSuccessfully() {
    when(toolRegistry.authorize(USER, "testTool")).thenReturn(testTool());

    String result = mcpService.executeTool(USER, "testTool", "{\"arg\":\"val\"}");

    assertThat(result).isEqualTo("execution result");
  }

  @Test
  void shouldThrowWhenExecutingNonexistentTool() {
    when(toolRegistry.authorize(USER, "nonexistent"))
        .thenThrow(new IllegalArgumentException("Tool not found: nonexistent"));

    assertThatThrownBy(() -> mcpService.executeTool(USER, "nonexistent", "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Tool not found: nonexistent");
  }

  @Test
  @DisplayName("a tool the actor may not use is refused, audited, and never run")
  void refusesAndAuditsWhenTheActorLacksTheRole() {
    when(toolRegistry.authorize(USER, "payroll"))
        .thenThrow(new AccessDeniedException("payroll", Reason.MISSING_ROLE));

    assertThatThrownBy(() -> mcpService.executeTool(USER, "payroll", "{}"))
        .isInstanceOf(AccessDeniedException.class);

    verify(audit).denied(Optional.of(USER), "tool.execute", "payroll", Reason.MISSING_ROLE);
    assertThat(runs).hasValue(0);
  }

  @Test
  @DisplayName("a call that acts for nobody is refused and audited")
  void refusesAndAuditsWhenThereIsNoActor() {
    when(toolRegistry.authorize(null, "searchKnowledge"))
        .thenThrow(new AccessDeniedException("searchKnowledge", Reason.NO_ACTOR));

    assertThatThrownBy(() -> mcpService.executeTool(null, "searchKnowledge", "{}"))
        .isInstanceOf(AccessDeniedException.class);

    verify(audit).denied(Optional.empty(), "tool.execute", "searchKnowledge", Reason.NO_ACTOR);
    assertThat(runs).hasValue(0);
  }
}
