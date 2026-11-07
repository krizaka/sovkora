package com.krizaka.sovkora.core.domain.model;

/** Policy used by the host to order the explicitly configured models for a step. */
public enum RoutingPolicy {
  /** Permit only models explicitly identified as sovereign and local. */
  SOVEREIGN_LOCAL_ONLY,
  /** Prefer cost according to the host's configured candidate order. */
  COST_OPTIMIZED,
  /** Prefer latency according to the host's configured candidate order. */
  LATENCY_FIRST
}
