package com.krizaka.sovkora.persistence.api.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable DTO representing a chat session state snapshot. Enforces self-validation per ERR-106.
 */
public record ChatSessionDto(String id, String userId, String title, Instant updatedAt)
    implements Serializable {

  public ChatSessionDto {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(userId, "userId cannot be null");
    Objects.requireNonNull(title, "title cannot be null");
    Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
  }
}
