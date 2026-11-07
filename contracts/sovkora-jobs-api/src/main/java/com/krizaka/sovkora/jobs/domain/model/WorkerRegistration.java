package com.krizaka.sovkora.jobs.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * What a worker declares about itself at boot — its {@code worker.yaml}, on the wire.
 *
 * <p>Tier-1 because a worker is <b>anything that honours the message contract</b>, in any language
 * (ADR-037 §3.4). This record is the Java view of a JSON body that the Python media worker posts
 * without linking a single Sovkora class, and it is documented as such in {@code
 * docs/WORKER_PROTOCOL.md}.
 *
 * <p>A worker declares its <b>bindings</b>, never a capability. Naming a capability is what coupled
 * the media worker to one pack and is forbidden by [PACK-002]/[PACK-003]; a binding is a statement
 * about queues, which is the worker's own business.
 *
 * @param name stable identity, the registry's primary key — a restarting worker re-registers rather
 *     than accumulating rows
 * @param family which family of work it serves, joined by name to {@code
 *     orazaka_capabilities.worker_family}
 * @param bindings the routing-key patterns it drains, e.g. {@code job.video.*}
 * @param version the worker's own version, so an operator can see a rolling upgrade
 * @param concurrency how many jobs it will run at once — an input to per-family autoscaling later
 */
public record WorkerRegistration(
    String name, String family, List<String> bindings, String version, int concurrency) {

  /** Every job routing key lives under this prefix (AGENTS.md §6). */
  private static final String BINDING_PREFIX = "job.";

  /** Compact canonical constructor enforcing the contract's invariants (ERR-106). */
  public WorkerRegistration {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("worker name must not be blank");
    }
    if (family == null || family.isBlank()) {
      throw new IllegalArgumentException("worker family must not be blank for " + name);
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("worker version must not be blank for " + name);
    }
    if (bindings == null || bindings.isEmpty()) {
      throw new IllegalArgumentException("worker " + name + " must declare at least one binding");
    }
    bindings = List.copyOf(bindings);
    for (String binding : bindings) {
      if (binding == null || !binding.startsWith(BINDING_PREFIX)) {
        throw new IllegalArgumentException(
            "binding must start with '" + BINDING_PREFIX + "', was: " + binding);
      }
    }
    if (concurrency < 1) {
      throw new IllegalArgumentException("concurrency must be >= 1, was: " + concurrency);
    }
  }

  /**
   * The bindings as a JSON array, for the {@code jsonb} column.
   *
   * <p>Hand-rolled rather than pulled from a JSON library because this record is Tier-1 and Tier-1
   * is pure JDK — and because the content is a validated list of routing-key patterns, which cannot
   * contain a character needing escaping: {@link #bindings} has already refused anything not
   * starting with {@code job.}, and a quote in the middle would make the binding meaningless
   * upstream long before it reached here.
   *
   * @return a JSON array literal, e.g. {@code ["job.video.*","job.compose.*"]}
   */
  public String bindingsAsJson() {
    return bindings.stream()
        .map(binding -> '"' + binding.replace("\\", "\\\\").replace("\"", "\\\"") + '"')
        .collect(Collectors.joining(",", "[", "]"));
  }

  /**
   * @param routingKey a concrete routing key, e.g. {@code job.video.generate}
   * @return whether one of this worker's bindings covers it
   */
  public boolean drains(String routingKey) {
    Objects.requireNonNull(routingKey, "routingKey must not be null");
    return bindings.stream().anyMatch(binding -> matches(binding, routingKey));
  }

  /** AMQP topic semantics, restricted to what a binding may contain: {@code *} is one segment. */
  private static boolean matches(String binding, String routingKey) {
    String[] pattern = binding.split("\\.");
    String[] key = routingKey.split("\\.");
    if (pattern.length != key.length) {
      return false;
    }
    for (int i = 0; i < pattern.length; i++) {
      if (!"*".equals(pattern[i]) && !pattern[i].equals(key[i])) {
        return false;
      }
    }
    return true;
  }
}
