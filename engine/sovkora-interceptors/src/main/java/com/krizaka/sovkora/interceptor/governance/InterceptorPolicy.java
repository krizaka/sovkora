package com.krizaka.sovkora.interceptor.governance;

import java.util.List;
import java.util.UUID;

/**
 * Interceptor policy — data-driven governance record for pipeline execution chains.
 *
 * @param id unique policy identifier.
 * @param interceptorName canonical interceptor name (e.g., "CostShieldInterceptor").
 * @param executionOrder pipeline execution order (ascending).
 * @param enabled whether this interceptor is active.
 * @param predicates list of data predicates that must ALL pass for activation.
 */
public record InterceptorPolicy(
    UUID id,
    String interceptorName,
    int executionOrder,
    boolean enabled,
    List<PolicyPredicate> predicates) {

  public InterceptorPolicy {
    if (id == null) {
      throw new IllegalArgumentException("InterceptorPolicy requires a non-null id");
    }
    if (interceptorName == null || interceptorName.isBlank()) {
      throw new IllegalArgumentException("InterceptorPolicy requires a non-blank interceptorName");
    }
    if (executionOrder < 0) {
      throw new IllegalArgumentException("executionOrder must be non-negative");
    }
    predicates = predicates != null ? List.copyOf(predicates) : List.of();
  }
}
