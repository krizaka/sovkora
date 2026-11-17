package com.krizaka.sovkora.engine.tools.infrastructure.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The local (stdio) MCP servers this deployment may start, by id. A platform server of the {@code
 * LOCAL} transport names one of these ids; the command and its arguments come from here, never from
 * the request that registered it. Empty by default: no process is started unless the deployment
 * declares one.
 *
 * @param stdio the declared servers, by id
 */
@ConfigurationProperties(prefix = "sovkora.mcp")
public record McpProperties(Map<String, StdioCommand> stdio) {

  /** Copies the declarations; none when the deployment declares none. */
  public McpProperties {
    stdio = stdio == null ? Map.of() : Map.copyOf(stdio);
  }

  /**
   * One declared local server.
   *
   * @param command the executable
   * @param args its arguments, in order
   */
  public record StdioCommand(String command, List<String> args) {

    /** Copies the arguments; none when the declaration lists none. */
    public StdioCommand {
      args = args == null ? List.of() : List.copyOf(args);
    }
  }
}
