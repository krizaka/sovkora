package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One audit line of a research fetch: never the query nor the path in clear (ADR-064, ADR-087 §4).
 */
@Entity
@Table(name = "research_fetch")
public class ResearchFetchEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "at")
  private Instant at;

  @Column(name = "actor_id")
  private String actorId;

  @Column(name = "run_id")
  private UUID runId;

  @Column(name = "chat_id")
  private String chatId;

  @Column(name = "source_id")
  private UUID sourceId;

  @Column(name = "operation")
  private String operation;

  @Column(name = "host")
  private String host;

  @Column(name = "path_hash")
  private String pathHash;

  @Column(name = "http_status")
  private Integer httpStatus;

  @Column(name = "bytes")
  private Integer bytes;

  @Column(name = "duration_ms")
  private Integer durationMs;

  @Column(name = "cache_hit")
  private boolean cacheHit;

  @Column(name = "refused_reason")
  private String refusedReason;

  public ResearchFetchEntity() {
    /* JPA requires a no-arg constructor */
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Instant getAt() {
    return at;
  }

  public void setAt(Instant at) {
    this.at = at;
  }

  public String getActorId() {
    return actorId;
  }

  public void setActorId(String actorId) {
    this.actorId = actorId;
  }

  public UUID getRunId() {
    return runId;
  }

  public void setRunId(UUID runId) {
    this.runId = runId;
  }

  public String getChatId() {
    return chatId;
  }

  public void setChatId(String chatId) {
    this.chatId = chatId;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public void setSourceId(UUID sourceId) {
    this.sourceId = sourceId;
  }

  public String getOperation() {
    return operation;
  }

  public void setOperation(String operation) {
    this.operation = operation;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getPathHash() {
    return pathHash;
  }

  public void setPathHash(String pathHash) {
    this.pathHash = pathHash;
  }

  public Integer getHttpStatus() {
    return httpStatus;
  }

  public void setHttpStatus(Integer httpStatus) {
    this.httpStatus = httpStatus;
  }

  public Integer getBytes() {
    return bytes;
  }

  public void setBytes(Integer bytes) {
    this.bytes = bytes;
  }

  public Integer getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(Integer durationMs) {
    this.durationMs = durationMs;
  }

  public boolean getCacheHit() {
    return cacheHit;
  }

  public void setCacheHit(boolean cacheHit) {
    this.cacheHit = cacheHit;
  }

  public String getRefusedReason() {
    return refusedReason;
  }

  public void setRefusedReason(String refusedReason) {
    this.refusedReason = refusedReason;
  }
}
