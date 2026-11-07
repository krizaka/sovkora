package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Policy version history — stores historical state snapshots for 1-click administrative rollbacks.
 *
 * <p>Each row captures a full JSONB snapshot of an interceptor policy with a SHA-256 validation
 * hash for integrity verification.
 */
@Entity
@Table(name = "policy_version_history")
public class PolicyVersionHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "policy_id", nullable = false)
  private UUID policyId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
  private String snapshot;

  @Column(name = "sha256_hash", nullable = false, length = 64)
  private String sha256Hash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false)
  private String createdBy;

  public UUID getId() {
    return id;
  }

  public UUID getPolicyId() {
    return policyId;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public String getSha256Hash() {
    return sha256Hash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public void setPolicyId(UUID policyId) {
    this.policyId = policyId;
  }

  public void setSnapshot(String snapshot) {
    this.snapshot = snapshot;
  }

  public void setSha256Hash(String sha256Hash) {
    this.sha256Hash = sha256Hash;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }
}
