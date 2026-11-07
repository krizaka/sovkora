package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** JPA Entity mapping the pipeline interceptor configuration table. */
@Entity
@Table(name = "pipeline_interceptor_config")
public class PipelineInterceptorConfigEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "interceptor_key", nullable = false, unique = true, length = 100)
  private String interceptorKey;

  @Column(name = "display_label", nullable = false, length = 200)
  private String displayLabel;

  @Column(name = "execution_order", nullable = false)
  private Integer executionOrder = 0;

  @Column(name = "is_enabled", nullable = false)
  private Boolean isEnabled = true;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getInterceptorKey() {
    return interceptorKey;
  }

  public void setInterceptorKey(String interceptorKey) {
    this.interceptorKey = interceptorKey;
  }

  public String getDisplayLabel() {
    return displayLabel;
  }

  public void setDisplayLabel(String displayLabel) {
    this.displayLabel = displayLabel;
  }

  public Integer getExecutionOrder() {
    return executionOrder;
  }

  public void setExecutionOrder(Integer executionOrder) {
    this.executionOrder = executionOrder;
  }

  public Boolean getIsEnabled() {
    return isEnabled;
  }

  public void setIsEnabled(Boolean isEnabled) {
    this.isEnabled = isEnabled;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
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
