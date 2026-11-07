package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA entity mapping the transactional outbox table (AGENTS.md §6, INTERFACES.md §9). */
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "aggregate_type", nullable = false, updatable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, updatable = false)
  private String aggregateId;

  @Column(name = "exchange", nullable = false, updatable = false)
  private String exchange;

  @Column(name = "routing_key", nullable = false, updatable = false)
  private String routingKey;

  @Column(name = "message_id", nullable = false, updatable = false, unique = true)
  private UUID messageId;

  @Column(name = "payload", columnDefinition = "jsonb", nullable = false, updatable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> payload;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  /** JPA-required no-arg constructor. */
  protected OutboxEventEntity() {}

  /** Creates a fresh, unpublished outbox row eligible for immediate relay. */
  public OutboxEventEntity(
      String aggregateType,
      String aggregateId,
      String exchange,
      String routingKey,
      Map<String, Object> payload,
      Instant createdAt) {
    this.id = UUID.randomUUID();
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.exchange = exchange;
    this.routingKey = routingKey;
    this.messageId = UUID.randomUUID();
    this.payload = payload;
    this.createdAt = createdAt;
    this.attempts = 0;
    this.nextAttemptAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public String getAggregateId() {
    return aggregateId;
  }

  public String getExchange() {
    return exchange;
  }

  public String getRoutingKey() {
    return routingKey;
  }

  public UUID getMessageId() {
    return messageId;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(Instant nextAttemptAt) {
    this.nextAttemptAt = nextAttemptAt;
  }
}
