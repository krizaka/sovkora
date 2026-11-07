package com.krizaka.sovkora.core.domain.model.job;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable domain representation of a background job/task state snapshot. Enforces self-validation
 * per ERR-106.
 */
public record JobInfo(
    String id,
    String userId,
    String featureKey,
    JobStatus status,
    Map<String, Object> payload,
    Map<String, Object> result,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt)
    implements Serializable {

  public JobInfo {
    Objects.requireNonNull(id, "Job ID cannot be null");
    if (id.isBlank()) {
      throw new IllegalArgumentException("Job ID cannot be blank");
    }
    Objects.requireNonNull(featureKey, "Feature key cannot be null");
    if (featureKey.isBlank()) {
      throw new IllegalArgumentException("Feature key cannot be blank");
    }
    Objects.requireNonNull(status, "Status cannot be null");
    Objects.requireNonNull(createdAt, "createdAt cannot be null");
    Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    payload = payload != null ? Map.copyOf(payload) : Map.of();
    result = result != null ? Map.copyOf(result) : Map.of();
  }
}
