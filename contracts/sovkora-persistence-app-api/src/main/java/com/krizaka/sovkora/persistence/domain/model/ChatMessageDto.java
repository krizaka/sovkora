package com.krizaka.sovkora.persistence.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable DTO for a single persisted conversation message (one turn line). {@code createdAt} may
 * be {@code null} on the way in (assigned by the provider) and is always populated on the way out.
 * Enforces self-validation per ERR-106.
 */
public record ChatMessageDto(String conversationId, String role, String content, Instant createdAt)
    implements Serializable {

  public ChatMessageDto {
    Objects.requireNonNull(conversationId, "conversationId cannot be null");
    Objects.requireNonNull(role, "role cannot be null");
    Objects.requireNonNull(content, "content cannot be null");
  }
}
