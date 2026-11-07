package com.krizaka.sovkora.persistence.domain.ports.inbound;

import com.krizaka.sovkora.jobs.domain.model.CapabilityDeclaration;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port for the capability registry — the single DB entry point for capabilities. Callers
 * (router adapters, controllers, listeners) depend on this port, never on the JPA repository or
 * entity.
 */
public interface CapabilityManager {

  /** Returns all registered capabilities. */
  List<CapabilityDeclaration> findAll();

  /** Looks up a single capability by its feature key. */
  Optional<CapabilityDeclaration> findByFeatureKey(String featureKey);

  /** Persists (insert or update) a capability and returns the stored state. */
  CapabilityDeclaration save(CapabilityDeclaration capability);

  /**
   * Removes one capability row.
   *
   * <p>Exists for install compensation, not for administration: when a pack install fails after
   * writing the capabilities it contributes, those rows have to come back out. Left behind, they
   * are routes nothing drains and handler keys no executor answers — the two conditions
   * [EXEC-001]/[EXEC-002] exist to catch.
   *
   * @param featureKey the capability to remove
   * @return {@code true} when a row was removed, {@code false} when there was none
   */
  boolean delete(String featureKey);
}
