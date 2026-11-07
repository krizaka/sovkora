package com.krizaka.sovkora.persistence.api.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Clean domain DTO record representing user-scoped MCP server connection state. */
public record UserMcpServerDto(
    Integer id,
    String userId,
    String label,
    String url,
    String authToken,
    Boolean enabled,
    Instant createdAt)
    implements Serializable {

  public UserMcpServerDto {
    Objects.requireNonNull(userId, "userId cannot be null");
    Objects.requireNonNull(label, "label cannot be null");
    Objects.requireNonNull(url, "url cannot be null");
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
  }
}
