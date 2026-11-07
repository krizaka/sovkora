package com.krizaka.sovkora.automationservice.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable record representing an automation job payload. */
public record AutomationJobPayload(
    String jobId,
    String userId,
    String connectorType,
    String action,
    AutomationJobStatus status,
    Map<String, Object> payload,
    Instant createdAt) {

  static final java.time.Clock DEFAULT_CLOCK = java.time.Clock.systemUTC();
  static java.time.Clock clock = DEFAULT_CLOCK;

  public AutomationJobPayload {
    Objects.requireNonNull(jobId, "jobId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(connectorType, "connectorType must not be null");
    Objects.requireNonNull(action, "action must not be null");
    Objects.requireNonNull(status, "status must not be null");
    payload = payload != null ? Map.copyOf(payload) : Map.of();
    createdAt = createdAt != null ? createdAt : Instant.now(clock);
  }
}
