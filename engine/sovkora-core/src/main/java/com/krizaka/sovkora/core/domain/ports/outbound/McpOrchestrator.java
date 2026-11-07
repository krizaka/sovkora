package com.krizaka.sovkora.core.domain.ports.outbound;

import java.util.List;

/**
 * Orchestrator interface for MCP (Model Context Protocol). Bridges external MCP server capabilities
 * into the Sovkora engine.
 */
public interface McpOrchestrator {

  /**
   * Resolves context from external MCP endpoints.
   *
   * @return A consolidated string containing external context.
   */
  String resolveExternalContext();

  /**
   * Resolves tools from external MCP endpoints.
   *
   * @return A list of external tool definitions.
   */
  List<Object> resolveExternalTools();
}
