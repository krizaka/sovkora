package com.krizaka.sovkora.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MCP tool contract: discovery + execute error handling. Asserts the tool catalog responds, and
 * that the execute endpoint rejects an <b>unknown</b> tool with a deterministic 4xx (never a 5xx) —
 * this exercises {@code POST /tools/{name}/execute} without firing a real, side-effecting tool run.
 * Executing a <b>real</b> tool needs a known tool + a valid input schema + provisioned MCP backend.
 */
class McpExecuteIT extends AbstractApiE2eTest {

  @Test
  @DisplayName("GET /api/v1/mcp/tools returns the tool catalog")
  void listTools() {
    String token = authenticate();
    APIResponse tools = request.get("/api/v1/mcp/tools", bearer(token));
    assertEquals(200, tools.status(), "Tool discovery must return 200");
    assertTrue(MAPPER.readTree(tools.text()).isArray(), "Tools payload must be a JSON array");
  }

  @Test
  @DisplayName("POST /api/v1/mcp/tools/{name}/execute rejects an unknown tool gracefully (4xx)")
  void executeUnknownToolIsGraceful() {
    String token = authenticate();
    APIResponse exec =
        request.post("/api/v1/mcp/tools/__e2e_nonexistent_tool__/execute", bearerJson(token, "{}"));
    int status = exec.status();
    assertTrue(
        status >= 400 && status < 500,
        "Executing an unknown tool must be a graceful client error (4xx), got " + status);
  }
}
