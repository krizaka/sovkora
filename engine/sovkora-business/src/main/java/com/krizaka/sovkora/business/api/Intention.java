package com.krizaka.sovkora.business.api;

import java.util.Objects;
import java.util.UUID;

/**
 * The immutable entry unit of the platform — the single thing the router translates transport into
 * and hands to the {@link UseCaseDispatcher}. Typed {@link IntentionType COMMAND|QUERY} (the CQRS
 * pivot) with a {@link Capability} and an {@link ExecutionMode SYNC|ASYNC}.
 *
 * @param id Correlation id (auto-generated when absent) — used for observability.
 * @param type CQRS pivot (COMMAND mutates, QUERY reads).
 * @param mode SYNC (interactive) or ASYNC (deferred to a worker).
 * @param capability The targeted capability.
 * @param goal Short human/business statement of intent.
 * @param payload The typed payload (required).
 * @param context Ambient session/actor/preferences context (required).
 */
public record Intention(
    String id,
    IntentionType type,
    ExecutionMode mode,
    Capability capability,
    String goal,
    Payload payload,
    IntentionContext context) {
  public Intention {
    id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(capability, "capability must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(context, "context must not be null");
  }
}
