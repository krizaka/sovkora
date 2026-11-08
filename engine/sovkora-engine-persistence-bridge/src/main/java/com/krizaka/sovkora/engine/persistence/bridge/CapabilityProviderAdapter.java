package com.krizaka.sovkora.engine.persistence.bridge;

import com.krizaka.sovkora.engine.core.domain.model.CapabilityDescriptor;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.CapabilityProvider;
import com.krizaka.sovkora.persistence.api.domain.ports.inbound.CapabilityManager;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Router-layer adapter implementing the core {@link CapabilityProvider} outbound port by delegating
 * to the persistence {@link CapabilityManager} inbound port — all DB access stays in the
 * persistence module.
 */
@Service
class CapabilityProviderAdapter implements CapabilityProvider {

  private final CapabilityManager capabilityManager;

  CapabilityProviderAdapter(CapabilityManager capabilityManager) {
    this.capabilityManager =
        Objects.requireNonNull(capabilityManager, "CapabilityManager cannot be null");
  }

  @Override
  public List<CapabilityDescriptor> findAll() {
    return capabilityManager.findAll().stream().map(CapabilityMapper::toDescriptor).toList();
  }
}
