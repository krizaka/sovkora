package com.krizaka.sovkora.business.api;

import java.util.Collection;
import java.util.Optional;

/**
 * Inbound port — the auto-discovered catalogue of registered {@link UseCase}s. Resolves the
 * use-case that serves a given {@link Intention} (by capability / descriptor), and exposes all
 * descriptors for routing, RBAC, and auto-documentation.
 */
public interface UseCaseRegistry {

  /**
   * Resolves the use-case that handles the given intention.
   *
   * @param intention The intention to route.
   * @return The matching use-case, or empty if none is registered for it.
   */
  Optional<UseCase<?, ?>> resolve(Intention intention);

  /**
   * @return All registered use-case descriptors.
   */
  Collection<UseCaseDescriptor> all();
}
