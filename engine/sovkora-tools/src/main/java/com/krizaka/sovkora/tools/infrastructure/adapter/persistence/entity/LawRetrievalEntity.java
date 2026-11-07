package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The cache of stage 2 (job-service {@code law_retrieval}). */
@Entity
@Table(name = "law_retrieval")
public class LawRetrievalEntity {

  @EmbeddedId private LawRetrievalId id;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "articles", nullable = false, columnDefinition = "jsonb")
  private String articles;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "claims", nullable = false, columnDefinition = "jsonb")
  private String claims;

  @Column(name = "run_id")
  private UUID runId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "stale", nullable = false)
  private boolean stale;

  public LawRetrievalEntity() {
    /* JPA requires a no-arg constructor */
  }

  public LawRetrievalId getId() {
    return id;
  }

  public void setId(LawRetrievalId id) {
    this.id = id;
  }

  public String getArticles() {
    return articles;
  }

  public void setArticles(String articles) {
    this.articles = articles;
  }

  public String getClaims() {
    return claims;
  }

  public void setClaims(String claims) {
    this.claims = claims;
  }

  public UUID getRunId() {
    return runId;
  }

  public void setRunId(UUID runId) {
    this.runId = runId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public boolean isStale() {
    return stale;
  }

  public void setStale(boolean stale) {
    this.stale = stale;
  }
}
