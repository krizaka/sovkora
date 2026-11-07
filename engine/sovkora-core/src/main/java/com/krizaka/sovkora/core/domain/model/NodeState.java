package com.krizaka.sovkora.core.domain.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Sealed interface representing the polymorphic state of a graph node in the Sovkora Operation
 * Graph.
 *
 * <p>Adheres strictly to the pattern-matching switch-expression mandates of [ERR-107].
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = NodeState.Active.class, name = "ACTIVE"),
  @JsonSubTypes.Type(value = NodeState.Locked.class, name = "LOCKED"),
  @JsonSubTypes.Type(value = NodeState.Invisible.class, name = "INVISIBLE")
})
public sealed interface NodeState permits NodeState.Active, NodeState.Locked, NodeState.Invisible {

  /** Active state — the capability is available for execution. No additional metadata. */
  record Active() implements NodeState {}

  /**
   * Locked state — the capability is temporarily disabled by an administrator.
   *
   * @param reason Human-readable explanation for the lock (required, non-blank).
   * @param lockedAt Audit timestamp of when the lock was applied (required).
   */
  record Locked(String reason, LocalDateTime lockedAt) implements NodeState {
    public Locked {
      Objects.requireNonNull(reason, "Lock reason cannot be null");
      if (reason.isBlank()) {
        throw new IllegalArgumentException("Lock reason cannot be blank");
      }
      Objects.requireNonNull(lockedAt, "Lock audit timestamp cannot be null");
    }
  }

  /** Invisible state — the capability is hidden from clients entirely (disabled in config). */
  record Invisible() implements NodeState {}
}
