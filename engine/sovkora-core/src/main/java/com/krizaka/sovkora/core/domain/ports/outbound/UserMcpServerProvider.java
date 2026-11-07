package com.krizaka.sovkora.core.domain.ports.outbound;

import java.util.List;

/** Outbound port to retrieve active user-specific MCP server connection configurations. */
public interface UserMcpServerProvider {

  record UserMcpServer(
      Integer id, String userId, String label, String url, String authToken, Boolean enabled) {}

  List<UserMcpServer> getActiveUserMcpServers(String userId);
}
