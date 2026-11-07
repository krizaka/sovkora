package com.krizaka.sovkora.persistence.api.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Domain DTO record representing a platform-wide MCP server configuration. */
public record PlatformMcpServerDto(
    Integer id,
    String label,
    String transportType,
    String url,
    String command,
    String args,
    String authToken,
    Boolean enabled,
    Instant createdAt)
    implements Serializable {

  public PlatformMcpServerDto {
    Objects.requireNonNull(label, "label cannot be null");
    Objects.requireNonNull(transportType, "transportType cannot be null");
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
  }
}
