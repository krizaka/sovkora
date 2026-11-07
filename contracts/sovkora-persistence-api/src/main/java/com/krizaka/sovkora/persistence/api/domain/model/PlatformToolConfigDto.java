package com.krizaka.sovkora.engine.persistence.api.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Domain DTO record representing a tool configuration. */
public record PlatformToolConfigDto(
    Integer id,
    String toolId,
    Boolean cacheEnabled,
    Integer cacheTtlSeconds,
    Boolean ragEnabled,
    String chunkerType,
    String sourceTable,
    Instant createdAt)
    implements Serializable {

  public PlatformToolConfigDto {
    Objects.requireNonNull(toolId, "toolId cannot be null");
    if (cacheEnabled == null) {
      cacheEnabled = Boolean.TRUE;
    }
    if (cacheTtlSeconds == null) {
      cacheTtlSeconds = 3600;
    }
    if (ragEnabled == null) {
      ragEnabled = Boolean.TRUE;
    }
    if (chunkerType == null) {
      chunkerType = "MARKDOWN_CHUNKERS";
    }
    if (sourceTable == null) {
      sourceTable = "orazaka_tools_rag_source";
    }
  }
}
