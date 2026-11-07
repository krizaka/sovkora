package com.krizaka.sovkora.jobs.api.domain.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Who a job runs as, and under what preferences — the Tier-1 half of the execution contract.
 *
 * <p><b>Why this exists rather than the engine's own {@code Context}.</b> The four in-process
 * executors that take a context never read a field from it; they forward it whole into an {@code
 * AiClient} request. Putting the engine's {@code Context} on this port would therefore make Tier-1
 * depend on {@code sovkora-engine-core} — Tier-2 — for a type it only passes through, and an out-of-tree
 * executor written in a repository that does not ship the engine could not implement the interface
 * at all. ADR-037 already recorded the principle: the extension point must not drag the engine
 * behind it.
 *
 * <p>So the shape travels, the engine type does not. The job service maps this onto its own {@code
 * Context} at the boundary — an anti-corruption layer of exactly the kind [ERR-127] asks for.
 * {@code authorities} are plain names for the same reason: the engine's {@code Authority} wrapper
 * is an engine type.
 *
 * @param actorId the opaque actor the work is attributed to
 * @param correlationId the id tying this execution to the conversation or run that asked for it
 * @param preferences execution overrides, including the {@code orazaka.*} keys a producer stamped
 *     into the payload; defensively copied and unmodifiable
 * @param authorities the actor's resolved role names; defensively copied and unmodifiable
 */
public record JobExecutionContext(
    String actorId,
    String correlationId,
    Map<String, Object> preferences,
    Set<String> authorities) {

  /** Compact canonical constructor enforcing the contract's invariants (ERR-106). */
  public JobExecutionContext {
    Objects.requireNonNull(actorId, "actorId must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    preferences =
        preferences == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(Map.copyOf(preferences));
    authorities =
        authorities == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(Set.copyOf(authorities));
  }

  /**
   * @param name the authority name to test
   * @return whether the actor holds it
   */
  public boolean hasAuthority(String name) {
    return authorities.contains(name);
  }
}
