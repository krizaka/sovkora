package com.krizaka.sovkora.core.domain.model.chat;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable DTO representing a chat session state snapshot. Enforces self-validation per ERR-106.
 */
public record ChatSessionInfo(String id, String userId, String title, Instant updatedAt)
    implements Serializable {

  public ChatSessionInfo {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(userId, "userId cannot be null");
    Objects.requireNonNull(title, "title cannot be null");
    Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
  }
}
