package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA Entity mapping the framework's asynchronous jobs database table. */
@Entity
@Table(name = "orazaka_jobs")
public class JobEntity {

  @Id
  @Column(name = "id", length = 36)
  private String id;

  @Column(name = "user_id")
  private String userId;

  @Column(name = "feature_key", nullable = false)
  private String featureKey;

  @Column(name = "status", nullable = false, length = 50)
  private String status;

  @Column(name = "payload", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> payload;

  @Column(name = "result", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> result;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  /** What the job's material is; the job plane's retention window is chosen by it (ADR-065). */
  @Column(name = "data_class", nullable = false, length = 20)
  private String dataClass;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDataClass() {
    return dataClass;
  }

  public void setDataClass(String dataClass) {
    this.dataClass = dataClass;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getFeatureKey() {
    return featureKey;
  }

  public void setFeatureKey(String featureKey) {
    this.featureKey = featureKey;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public void setPayload(Map<String, Object> payload) {
    this.payload = payload;
  }

  public Map<String, Object> getResult() {
    return result;
  }

  public void setResult(Map<String, Object> result) {
    this.result = result;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
