package com.krizaka.sovkora.core.application.service;

import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.mcp.McpToolInfo;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.inbound.McpService;
import com.krizaka.sovkora.core.domain.ports.outbound.AccessAudit;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Concrete implementation of {@link McpService}. Reads the tools through the {@link ToolRegistry}
 * port.
 *
 * <p>The permission check is the registry's ({@link ToolRegistry#authorize}); this service is the
 * one place that turns a refusal into an audit line, then lets it propagate. A tool is never run
 * without having been authorized for the actor first.
 */
public class DefaultMcpService implements McpService {

  private static final String EXECUTE = "tool.execute";

  private final ToolRegistry toolRegistry;
  private final AccessAudit audit;

  public DefaultMcpService(ToolRegistry toolRegistry, AccessAudit audit) {
    this.toolRegistry = Objects.requireNonNull(toolRegistry, "ToolRegistry must not be null");
    this.audit = Objects.requireNonNull(audit, "AccessAudit must not be null");
  }

  @Override
  public List<McpToolInfo> getAvailableTools(Actor actor) {
    return toolRegistry.getRegisteredTools(actor).stream()
        .map(tool -> new McpToolInfo(tool.name(), tool.description(), tool.inputSchema()))
        .toList();
  }

  @Override
  public String executeTool(Actor actor, String name, String argumentsJson) {
    EngineTool tool;
    try {
      tool = toolRegistry.authorize(actor, name);
    } catch (AccessDeniedException refused) {
      audit.denied(Optional.ofNullable(actor), EXECUTE, name, refused.reason());
      throw refused;
    }
    return tool.call(argumentsJson);
  }
}
