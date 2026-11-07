package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.RoutingPolicy;
import com.krizaka.sovkora.core.domain.model.StepModelCandidate;
import java.util.List;

/** Supplies host-owned routing configuration without a catalog or global fallback. */
@FunctionalInterface
public interface StepRouteProvider {

  /**
   * Reads current configuration for a capability and policy, once per step execution.
   *
   * @param capability requested capability
   * @param policy effective, nonnull routing policy
   * @return explicitly declared candidates in attempt order; empty or null means no route
   */
  List<StepModelCandidate> candidates(String capability, RoutingPolicy policy);
}
