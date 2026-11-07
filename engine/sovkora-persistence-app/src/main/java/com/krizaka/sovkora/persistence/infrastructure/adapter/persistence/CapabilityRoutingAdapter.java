package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence;

import com.krizaka.sovkora.jobs.domain.model.CapabilityDeclaration;
import com.krizaka.sovkora.jobs.domain.model.CapabilityRoute;
import com.krizaka.sovkora.jobs.domain.port.CapabilityRoutingClient;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CapabilityManager;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reads a capability's route straight from the registry, for hosts that own {@code orazaka_db}.
 *
 * <p>The authoritative implementation of {@link CapabilityRoutingClient}: it reads the rows, and
 * every other implementation is a cache in front of an HTTP hop to a host running this one. That
 * asymmetry is deliberate — the routing table has exactly one owner (AGENTS.md §4/§5), and a
 * producer in another bounded context asks rather than keeping a second copy.
 *
 * <p><b>A disabled capability has no route.</b> Returning its row with {@code enabled=false} and
 * leaving the caller to check would make "did anyone remember to look?" the property that decides
 * whether a disabled capability still gets dispatched. Empty is the only answer that cannot be
 * ignored by accident.
 */
@Component
class CapabilityRoutingAdapter implements CapabilityRoutingClient {

  private final CapabilityManager capabilityManager;

  CapabilityRoutingAdapter(CapabilityManager capabilityManager) {
    this.capabilityManager =
        Objects.requireNonNull(capabilityManager, "CapabilityManager cannot be null");
  }

  @Override
  public Optional<CapabilityRoute> route(String featureKey) {
    if (featureKey == null || featureKey.isBlank()) {
      return Optional.empty();
    }
    return capabilityManager
        .findByFeatureKey(featureKey)
        .filter(CapabilityDeclaration::enabled)
        .map(CapabilityRoutingAdapter::toRoute);
  }

  private static CapabilityRoute toRoute(CapabilityDeclaration capability) {
    return new CapabilityRoute(
        capability.featureKey(),
        capability.routingKey(),
        capability.billableUnit(),
        capability.billableCapability(),
        capability.latencyClass(),
        capability.enabled());
  }
}
