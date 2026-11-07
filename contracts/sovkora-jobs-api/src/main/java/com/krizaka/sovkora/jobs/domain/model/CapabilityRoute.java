package com.krizaka.sovkora.jobs.domain.model;

/**
 * Where one capability's work is executed — the row that replaced a substring heuristic.
 *
 * <p>Routing used to be a {@code contains()} chain over the feature key, which meant a capability
 * the chain did not recognise was <b>silently</b> published to the text queue, where a worker that
 * could not execute it failed it — or an LLM answered something plausible instead (ADR-037). The
 * mapping is now data, so adding a capability is a row and never a deploy (AGENTS.md §4).
 *
 * <p>{@code routingKey} must start with {@code job.} because that is the whole of the job plane's
 * key grammar ({@code job.\{capability\}.\{action\}}, AGENTS.md §6): a key outside it binds to no
 * queue, and a message published to no queue is discarded by the broker without an error.
 *
 * @param featureKey the capability this routes, opaque everywhere outside the registry
 * @param routingKey the {@code orazaka.jobs} routing key the dispatcher publishes under
 * @param billableUnit the unit consumption is measured in before the pricebook converts it to
 *     credits, or {@code null} when the unit is a property of the model rather than the capability
 *     (speech bills characters, transcription bills minutes)
 * @param billableCapability which {@code credit_pricebook} row prices this capability's work, or
 *     {@code null} when the capability is not metered. Carried beside {@code billableUnit} because
 *     it is the other half of the same row and the same question — a caller holding a measurement
 *     needs BOTH to know what it costs. Its absence is why a Studio run could only settle against
 *     the run hold's own capability: the saga had no way to learn that a vision step prices as
 *     IMAGE and a script step as CHAT (ADR-041).
 * @param latencyClass which LANE this capability's work waits in — {@code INTERACTIVE} or {@code
 *     BATCH} (ADR-067). Carried on the route because the lane is decided where the routing key is,
 *     and because a consumer of this contract needs it for a reason the routing key cannot answer:
 *     how long the work may legitimately take. {@code null} reads as {@code BATCH}, the fail-closed
 *     direction — an unclassified capability waits where waiting is expected
 * @param enabled whether the capability may be dispatched at all — a disabled row refuses at
 *     publish and at dispatch rather than routing somewhere plausible
 */
public record CapabilityRoute(
    String featureKey,
    String routingKey,
    String billableUnit,
    String billableCapability,
    String latencyClass,
    boolean enabled) {

  /** The lane an unclassified capability waits in. */
  public static final String DEFAULT_LATENCY_CLASS = "BATCH";

  /** Every job routing key lives under this prefix (AGENTS.md §6). */
  private static final String ROUTING_KEY_PREFIX = "job.";

  /** Compact canonical constructor enforcing the contract's invariants (ERR-106). */
  public CapabilityRoute {
    latencyClass =
        latencyClass == null || latencyClass.isBlank() ? DEFAULT_LATENCY_CLASS : latencyClass;
    if (featureKey == null || featureKey.isBlank()) {
      throw new IllegalArgumentException("featureKey must not be blank");
    }
    if (routingKey == null || routingKey.isBlank()) {
      throw new IllegalArgumentException("routingKey must not be blank for " + featureKey);
    }
    if (!routingKey.startsWith(ROUTING_KEY_PREFIX)) {
      throw new IllegalArgumentException(
          "routingKey must start with '" + ROUTING_KEY_PREFIX + "', was: " + routingKey);
    }
    if (billableUnit != null && billableUnit.isBlank()) {
      throw new IllegalArgumentException(
          "billableUnit must be absent or named, never blank, for " + featureKey);
    }
  }
}
