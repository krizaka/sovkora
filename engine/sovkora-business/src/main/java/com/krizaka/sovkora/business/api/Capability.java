package com.krizaka.sovkora.business.api;

/**
 * The high-level capability an {@link Intention} targets. Mirrors the operation-graph capability
 * space and is the routing pivot the {@link UseCaseRegistry} matches use-cases against.
 */
public enum Capability {
  CHAT,
  IMAGE,
  AUDIO,
  VIDEO,
  AGENT,
  ADMIN,
  /**
   * A run of an installed Studio — a declarative DAG over the other capabilities (ADR-034). It is a
   * capability rather than a Studio-only REST surface so a run is a first-class {@link Intention},
   * reachable from the controller, the CLI and the agent loop alike; not routing it through the
   * {@code Intention} abstraction would be the mistake.
   */
  STUDIO
}
