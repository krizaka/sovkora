package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA Entity mapping the {@code orazaka_tools_cache} database table.
 *
 * <p>Caches tool execution results to prevent redundant calls.
 */
@Entity
@Table(name = "orazaka_tools_cache")
public class ToolCacheEntity {

  @EmbeddedId private ToolCacheId id;

  @Column(name = "cache_value", nullable = false)
  private String cacheValue;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** Default constructor required by JPA/Hibernate. */
  public ToolCacheEntity() {
    /* JPA requires no-arg constructor */
  }

  public ToolCacheId getId() {
    return id;
  }

  public void setId(ToolCacheId id) {
    this.id = id;
  }

  public String getCacheValue() {
    return cacheValue;
  }

  public void setCacheValue(String cacheValue) {
    this.cacheValue = cacheValue;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}
