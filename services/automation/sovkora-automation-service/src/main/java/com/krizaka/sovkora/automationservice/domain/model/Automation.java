package com.krizaka.sovkora.automationservice.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A cloud automation: what runs ({@code studioKey} with its fixed {@code inputs}) for one owner.
 * How it starts is the business of its triggers (ADR-084).
 *
 * @param id the automation
 * @param ownerId the opaque actor id that owns it
 * @param name the owner's label
 * @param studioKey the Studio a run of this automation executes
 * @param inputs the fixed answers to that Studio's form
 * @param enabled whether its triggers may start runs
 * @param createdAt when it was created
 * @param updatedAt when it last changed
 */
public record Automation(
    UUID id,
    String ownerId,
    String name,
    String studioKey,
    Map<String, Object> inputs,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt) {

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public Automation {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(ownerId, "ownerId is required");
    Objects.requireNonNull(name, "name is required");
    Objects.requireNonNull(studioKey, "studioKey is required");
    Objects.requireNonNull(createdAt, "createdAt is required");
    Objects.requireNonNull(updatedAt, "updatedAt is required");
    inputs = inputs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
  }
}
