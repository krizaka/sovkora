package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** JPA Entity mapping the {@code platform_tool_configs} database table. */
@Entity
@Table(name = "platform_tool_configs")
public class PlatformToolConfigEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "tool_id", nullable = false, unique = true, length = 255)
  private String toolId;

  @Column(name = "cache_enabled")
  private Boolean cacheEnabled = true;

  @Column(name = "cache_ttl_seconds")
  private Integer cacheTtlSeconds = 3600;

  @Column(name = "rag_enabled")
  private Boolean ragEnabled = true;

  @Column(name = "chunker_type", length = 100)
  private String chunkerType = "MARKDOWN_CHUNKERS";

  @Column(name = "source_table", length = 255)
  private String sourceTable = "orazaka_tools_rag_source";

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  public PlatformToolConfigEntity() {
    /* JPA requires no-arg constructor */
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getToolId() {
    return toolId;
  }

  public void setToolId(String toolId) {
    this.toolId = toolId;
  }

  public Boolean getCacheEnabled() {
    return cacheEnabled;
  }

  public void setCacheEnabled(Boolean cacheEnabled) {
    this.cacheEnabled = cacheEnabled;
  }

  public Integer getCacheTtlSeconds() {
    return cacheTtlSeconds;
  }

  public void setCacheTtlSeconds(Integer cacheTtlSeconds) {
    this.cacheTtlSeconds = cacheTtlSeconds;
  }

  public Boolean getRagEnabled() {
    return ragEnabled;
  }

  public void setRagEnabled(Boolean ragEnabled) {
    this.ragEnabled = ragEnabled;
  }

  public String getChunkerType() {
    return chunkerType;
  }

  public void setChunkerType(String chunkerType) {
    this.chunkerType = chunkerType;
  }

  public String getSourceTable() {
    return sourceTable;
  }

  public void setSourceTable(String sourceTable) {
    this.sourceTable = sourceTable;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
