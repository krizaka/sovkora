package com.krizaka.sovkora.core.domain.model;

import java.util.Objects;

/**
 * Defines a capability node inside the Sovkora Operation Graph.
 *
 * @param id The unique identifier of the operation node (e.g., "orazaka.core.chat.image").
 * @param presentationContext The display context (defaults to "CONTEXT_MENU_PLUS").
 * @param state The current runtime state of the capability.
 */
public final record OperationNode(String id, String presentationContext, NodeState state) {
  public OperationNode {
    Objects.requireNonNull(id, "Operation Node ID cannot be null");
    if (id.isBlank()) {
      throw new IllegalArgumentException("Operation Node ID cannot be blank");
    }
    if (presentationContext == null || presentationContext.isBlank()) {
      presentationContext = "CONTEXT_MENU_PLUS";
    }
    Objects.requireNonNull(state, "Operation Node state cannot be null");
  }
}
