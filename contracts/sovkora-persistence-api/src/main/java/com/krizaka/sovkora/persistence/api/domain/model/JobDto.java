package com.krizaka.sovkora.persistence.api.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable DTO representing a framework job state snapshot. Enforces compact constructor
 * validation per ERR-106.
 */
public record JobDto(
    String id,
    String userId,
    String featureKey,
    String status,
    Map<String, Object> payload,
    Map<String, Object> result,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt)
    implements Serializable {

  public JobDto {
    Objects.requireNonNull(id, "Job ID cannot be null");
    if (id.isBlank()) {
      throw new IllegalArgumentException("Job ID cannot be blank");
    }
    Objects.requireNonNull(featureKey, "Feature key cannot be null");
    if (featureKey.isBlank()) {
      throw new IllegalArgumentException("Feature key cannot be blank");
    }
    Objects.requireNonNull(status, "Status cannot be null");
    if (status.isBlank()) {
      throw new IllegalArgumentException("Status cannot be blank");
    }
    Objects.requireNonNull(createdAt, "createdAt cannot be null");
    Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    payload = payload != null ? Map.copyOf(payload) : Map.of();
    result = result != null ? Map.copyOf(result) : Map.of();
  }
}
