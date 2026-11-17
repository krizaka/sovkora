package com.krizaka.sovkora.engine.tools.infrastructure.config;

import com.krizaka.sovkora.engine.core.domain.ports.outbound.McpServerPolicy.StdioLaunch;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The local (stdio) MCP servers this deployment may start, by id. A platform server of the {@code
 * LOCAL} transport names one of these ids; the command and its arguments come from here, never from
 * the request that registered it. Empty by default: no process is started unless the deployment
 * declares one, and a declaration without a command fails the startup.
 *
 * @param stdio the declared servers, by id ({@code sovkora.mcp.stdio.<id>.command} and {@code
 *     .args})
 */
@ConfigurationProperties(prefix = "sovkora.mcp")
public record McpProperties(Map<String, StdioLaunch> stdio) {

  /** Copies the declarations; none when the deployment declares none. */
  public McpProperties {
    stdio = stdio == null ? Map.of() : Map.copyOf(stdio);
  }
}
