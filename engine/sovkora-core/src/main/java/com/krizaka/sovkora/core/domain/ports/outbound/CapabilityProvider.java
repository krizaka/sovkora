package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.CapabilityDescriptor;
import java.util.List;

/**
 * Outbound port resolving the operation-graph capability registry from the system of record (the
 * database).
 *
 * <p>Returns full {@link CapabilityDescriptor descriptors} — blueprint plus enabled state — so the
 * {@code GraphEngine} no longer reads static yaml blueprints nor performs a separate toggle lookup.
 * Supersedes both {@code FeaturesProperties} and the former {@code FeatureToggleProvider}.
 */
public interface CapabilityProvider {

  /**
   * Lists every registered capability with its blueprint and enabled state.
   *
   * @return All capability descriptors (empty if none are registered).
   */
  List<CapabilityDescriptor> findAll();
}
