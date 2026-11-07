package com.krizaka.sovkora.jobs.api.api.domain.model;

import java.util.regex.Pattern;

/**
 * One capability row as a pack declares it (ADR-037 §3.2).
 *
 * <p>Carries both dispatch discriminants of ADR-038, because neither is derivable from the other:
 * {@code routingKey} selects the PROCESS that drains the job and {@code handlerKey} selects the
 * CODE PATH inside it. A declaration with one but not the other describes a job that either reaches
 * no worker or reaches one that cannot run it, which is why both are required rather than
 * defaulted.
 *
 * @param featureKey the capability key, unique in {@code orazaka_capabilities}
 * @param handlerKey the {@code JobExecutor} whose {@code handlerKey()} equals this
 * @param routingKey the AMQP routing key the job is published under
 * @param billableUnit what is metered, {@code null} where the unit belongs to the model
 * @param billableCapability which pricebook row prices it
 * @param inputSchema what a caller MAY PASS, as a JSON Schema — types, {@code required}, defaults
 *     and the {@code format: prose | asset-id} a composer reads to know which of its two holdings
 *     fills an input (ADR-068 §3). This is what {@code payloadTemplate} carried as an untyped
 *     string of {@code ${placeholders}}, which no rule could check (ADR-069)
 * @param outputSchema what a SUCCESSFUL execution publishes, with types. Nothing declared this
 *     before, so every {@code {{steps.<out>.<field>}}} link in every blueprint referenced a field
 *     no contract mentioned — and the one hand-written table that tried was wrong about audio
 *     analysis. {@code {}} means "declares nothing", and a step over it fails the fitness function
 *     rather than passing silently
 * @param latencyClass which LANE its work waits in — INTERACTIVE or BATCH (ADR-067). Read-only from
 *     a pack's point of view: {@code CapabilityRegistryService} overwrites whatever a registration
 *     carries, because a pack that chose its own lane would choose the interactive one and then
 *     everything would be interactive. The platform reclassifies from measured durations
 * @param enabled whether it may be dispatched
 */
public record CapabilityDeclaration(
    String featureKey,
    String handlerKey,
    String routingKey,
    String billableUnit,
    String billableCapability,
    String latencyClass,
    String inputSchema,
    String outputSchema,
    boolean enabled) {

  private static final Pattern FEATURE_KEY =
      Pattern.compile("^orazaka\\.[a-z0-9]+(\\.[a-z0-9]+)+$");
  private static final Pattern ROUTING_KEY = Pattern.compile("^job\\.[a-z0-9]+\\.[a-z0-9]+$");

  /** Compact canonical constructor enforcing the declaration's invariants (ERR-106). */
  public CapabilityDeclaration {
    // The endpoint rule was here — "both halves, or neither", written four times across this
    // repository and wrong in three of them. It is gone with uriPath and httpMethod (ADR-069 §5):
    // a capability has no HTTP surface any more, because invoking one is starting a run, and the
    // run path is AMQP behind a Studio. An invariant with no field to guard is not kept "in case".
    if (featureKey == null || !FEATURE_KEY.matcher(featureKey).matches()) {
      throw new IllegalArgumentException("featureKey must be a dotted orazaka key: " + featureKey);
    }
    if (routingKey == null || !ROUTING_KEY.matcher(routingKey).matches()) {
      throw new IllegalArgumentException(
          featureKey + " must declare a job.<family>.<action> routingKey, not " + routingKey);
    }
    if (handlerKey == null || handlerKey.isBlank()) {
      throw new IllegalArgumentException(featureKey + " declares no handlerKey");
    }
    // `{}` is the undeclared contract, and it must be representable: a pack that declares nothing
    // gets a capability whose steps fail the fitness function, which is the fail-closed direction.
    // `null` is not — it would reach a NOT NULL column as an error nobody can read (ADR-069 §4).
    inputSchema = inputSchema == null || inputSchema.isBlank() ? "{}" : inputSchema;
    outputSchema = outputSchema == null || outputSchema.isBlank() ? "{}" : outputSchema;
  }
}
