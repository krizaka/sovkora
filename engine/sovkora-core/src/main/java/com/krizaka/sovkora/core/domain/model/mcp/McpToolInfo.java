package com.krizaka.sovkora.core.domain.model.mcp;

import java.util.Objects;

/** DTO representing basic tool definition info, decoupled from Spring AI. */
public record McpToolInfo(String name, String description, String inputSchema) {
  public McpToolInfo {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(description, "description must not be null");
    Objects.requireNonNull(inputSchema, "inputSchema must not be null");
  }
}
