package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity mapping the {@code validation_pipeline_configs} table.
 *
 * <p>Each row represents a single tier in the 4-tier validation matrix. The {@code step_type}
 * column is stored as a plain {@code VARCHAR} string to avoid coupling the persistence layer to
 * core domain enums (§1.2 Module Separation Invariant). The router mapper converts between this
 * string and the domain {@code ValidationStepType} enum.
 *
 * @since 1.1.0
 */
@Entity
@Table(name = "validation_pipeline_configs")
public class ValidationPipelineConfigEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "step_type", nullable = false, unique = true, length = 20)
  private String stepType;

  @Column(name = "is_enabled", nullable = false)
  private Boolean isEnabled = true;

  @Column(name = "execution_order", nullable = false)
  private Integer executionOrder = 0;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "configuration_payload", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> configurationPayload = Map.of();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getStepType() {
    return stepType;
  }

  public void setStepType(String stepType) {
    this.stepType = stepType;
  }

  public Boolean getIsEnabled() {
    return isEnabled;
  }

  public void setIsEnabled(Boolean isEnabled) {
    this.isEnabled = isEnabled;
  }

  public Integer getExecutionOrder() {
    return executionOrder;
  }

  public void setExecutionOrder(Integer executionOrder) {
    this.executionOrder = executionOrder;
  }

  public Map<String, Object> getConfigurationPayload() {
    return configurationPayload;
  }

  public void setConfigurationPayload(Map<String, Object> configurationPayload) {
    this.configurationPayload = configurationPayload;
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
