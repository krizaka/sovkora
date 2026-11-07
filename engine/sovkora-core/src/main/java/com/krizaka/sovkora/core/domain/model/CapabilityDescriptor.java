package com.krizaka.sovkora.core.domain.model;

import java.util.Objects;

/**
 * Describes a single operation-graph capability: its identity and whether the platform dispatches
 * it.
 *
 * <p>It carried a label, an icon, a path and a verb until ADR-069 §5, because the registry did:
 * this record was the engine's view of the table's UI-manifest half. The endpoints it described
 * were deleted with door 1 (ADR-068) and a capability's display name lives in the pack's i18n, so
 * what is left of a capability <i>to the engine</i> is which one it is and whether it runs.
 *
 * @param featureKey Unique capability ID (e.g. {@code "orazaka.core.media.video"}).
 * @param enabled Whether the capability is active (renders an evaluated node) or invisible.
 */
public record CapabilityDescriptor(String featureKey, boolean enabled) {

  /** Compact constructor — the identity must be there; nothing else is left to validate. */
  public CapabilityDescriptor {
    Objects.requireNonNull(featureKey, "Capability feature key cannot be null");
    if (featureKey.isBlank()) {
      throw new IllegalArgumentException("Capability feature key cannot be blank");
    }
  }
}
