package com.krizaka.sovkora.business.domain.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rich, self-validating domain context for Workflow orchestration.
 *
 * <p>This record represents the <b>Business layer's</b> complete declaration of intent for a
 * workflow execution. It carries all business-level directives (identity, tier, interceptor
 * policies, and arbitrary metadata) without importing or depending on any Core infrastructure type.
 *
 * <p>The Router's {@code WorkflowAdapter} is the sole translator between this business-owned
 * context and the Core's infrastructure {@code Context} record, mapping fields into the {@code
 * orazaka.pipeline.*} and {@code orazaka.user.*} namespaces.
 *
 * <p><strong>Thread Safety</strong>: This record is immutable. All collection fields are
 * defensively copied in the compact constructor and wrapped in unmodifiable views. Safe for
 * concurrent access on Virtual Threads.
 *
 * @param contextId Unique workflow execution identifier (e.g. conversation or session ID).
 * @param systemInstructions The system-level prompt instructions governing this workflow.
 * @param userTier The user's subscription or RBAC tier (e.g. "FREE", "PRO", "ENTERPRISE").
 * @param forcedInterceptors Interceptor keys that must execute regardless of routing decisions.
 * @param skippedInterceptors Interceptor keys that must be bypassed for this execution.
 * @param metadata Arbitrary key-value pairs carrying domain-specific business context.
 * @since 1.1.0
 */
public record WorkflowContext(
    String contextId,
    String systemInstructions,
    String userTier,
    Set<String> forcedInterceptors,
    Set<String> skippedInterceptors,
    Map<String, Object> metadata) {

  /**
   * Compact constructor enforcing non-null invariants, default tier assignment, and defensive
   * copying of all collection fields.
   */
  public WorkflowContext {
    Objects.requireNonNull(contextId, "contextId must not be null");
    Objects.requireNonNull(systemInstructions, "systemInstructions must not be null");
    userTier = userTier == null || userTier.isBlank() ? "DEFAULT" : userTier.strip();
    forcedInterceptors =
        forcedInterceptors != null
            ? Collections.unmodifiableSet(Set.copyOf(forcedInterceptors))
            : Collections.emptySet();
    skippedInterceptors =
        skippedInterceptors != null
            ? Collections.unmodifiableSet(Set.copyOf(skippedInterceptors))
            : Collections.emptySet();
    metadata =
        metadata != null
            ? Collections.unmodifiableMap(Map.copyOf(metadata))
            : Collections.emptyMap();
  }

  /**
   * Convenience factory for minimal workflow context with default tier and no interceptor
   * overrides.
   *
   * @param contextId The workflow execution identifier.
   * @param systemInstructions The system-level prompt instructions.
   * @return A minimal {@link WorkflowContext} with safe defaults.
   */
  public static WorkflowContext minimal(String contextId, String systemInstructions) {
    return new WorkflowContext(
        contextId, systemInstructions, "DEFAULT", Set.of(), Set.of(), Map.of());
  }
}
