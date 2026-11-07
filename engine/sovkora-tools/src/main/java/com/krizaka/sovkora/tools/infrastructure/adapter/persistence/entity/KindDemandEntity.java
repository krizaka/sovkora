package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The unsupported and unknown kinds, counted (job-service {@code kind_demand}, ADR-089 §6). Never a
 * document, never its content.
 */
@Entity
@Table(name = "kind_demand")
public class KindDemandEntity {

  @EmbeddedId private KindDemandId id;

  @Column(name = "count", nullable = false)
  private long count;

  @Column(name = "first_seen", nullable = false)
  private Instant firstSeen;

  @Column(name = "last_seen", nullable = false)
  private Instant lastSeen;

  public KindDemandEntity() {
    /* JPA requires a no-arg constructor */
  }

  public KindDemandEntity(KindDemandId id, long count, Instant firstSeen, Instant lastSeen) {
    this.id = id;
    this.count = count;
    this.firstSeen = firstSeen;
    this.lastSeen = lastSeen;
  }

  public KindDemandId getId() {
    return id;
  }

  public long getCount() {
    return count;
  }

  public Instant getFirstSeen() {
    return firstSeen;
  }

  public Instant getLastSeen() {
    return lastSeen;
  }
}
