package com.krizaka.sovkora.business.api;

import java.util.Objects;
import java.util.Set;

/**
 * Metadata describing a {@link UseCase} — the metadata-driven contract the {@link UseCaseRegistry}
 * matches intentions against and the auto-docs surface. Declaring a descriptor is how a product
 * registers a new use-case (no core/router change).
 *
 * @param id Stable use-case id (required, non-blank).
 * @param capability The capability this use-case serves (required).
 * @param personas Persona keys whose prompt fragments apply; never null.
 * @param planning Deterministic workflow vs delegated agentic planning (required).
 * @param requiredTools Tool ids the use-case needs available; never null.
 * @param rbac RBAC policy gating execution (required).
 */
public record UseCaseDescriptor(
    String id,
    Capability capability,
    Set<String> personas,
    PlanningMode planning,
    Set<String> requiredTools,
    RbacPolicy rbac) {
  public UseCaseDescriptor {
    Objects.requireNonNull(id, "id must not be null");
    if (id.isBlank()) {
      throw new IllegalArgumentException("UseCaseDescriptor id must not be blank");
    }
    Objects.requireNonNull(capability, "capability must not be null");
    Objects.requireNonNull(planning, "planning must not be null");
    Objects.requireNonNull(rbac, "rbac must not be null");
    personas = personas == null ? Set.of() : Set.copyOf(personas);
    requiredTools = requiredTools == null ? Set.of() : Set.copyOf(requiredTools);
  }
}
