package com.krizaka.sovkora.core.domain.ports.outbound;

import java.util.List;

/** Outbound port to retrieve platform-wide MCP server configurations. */
public interface PlatformMcpServerProvider {

  record PlatformMcpServer(
      Integer id,
      String label,
      String transportType,
      String url,
      String command,
      String args,
      String authToken,
      Boolean enabled) {}

  List<PlatformMcpServer> getActivePlatformMcpServers();
}
