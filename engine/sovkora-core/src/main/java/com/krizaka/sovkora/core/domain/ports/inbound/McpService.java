package com.krizaka.sovkora.core.domain.ports.inbound;

import com.krizaka.sovkora.core.domain.model.mcp.McpToolInfo;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import java.util.List;

/**
 * Inbound port interface for interacting with and executing MCP and local tools. Every method is
 * made for an {@link Actor} (ADR-091): a tool the actor may not use is absent from the listing and
 * refused on execution, and a call that acts for nobody gets nothing.
 */
public interface McpService {

  /**
   * Retrieves the registered tools the actor may use.
   *
   * @param actor who asks; {@code null} gets none
   * @return A list of {@link McpToolInfo} representing the tools.
   */
  List<McpToolInfo> getAvailableTools(Actor actor);

  /**
   * Executes a registered tool by its name with the given JSON arguments, for the actor.
   *
   * @param actor who asks; {@code null} is refused
   * @param name The name of the tool to execute.
   * @param argumentsJson The JSON string containing tool arguments.
   * @return The execution result string.
   * @throws IllegalArgumentException when no such tool exists
   * @throws AccessDeniedException when the actor may not use it; the refusal is audited
   */
  String executeTool(Actor actor, String name, String argumentsJson);
}
