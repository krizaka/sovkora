package com.krizaka.sovkora.jobs.api.domain.port;

import com.krizaka.sovkora.jobs.api.domain.model.CapabilityDeclaration;

/**
 * Write side of the capability registry: a pack contributing a capability it brings.
 *
 * <p>The complement of {@link CapabilityRoutingClient}, and deliberately a separate port. Reading a
 * route sits on the dispatch hot path of every step of every run; writing one happens when a pack
 * is installed. Merging them would put an admin-speed write behind an interface whose whole design
 * — a cache, a short TTL, a refusal that never fails open — exists to serve the read.
 *
 * <p>Implementations reach the context that owns {@code orazaka_capabilities} over its {@code
 * /internal/v1} surface. The studio context does not write that table directly: it is another
 * bounded context's, and a cross-context write is precisely what SEAM-001 forbids.
 */
public interface CapabilityRegistrationClient {

  /**
   * Creates or replaces one capability row.
   *
   * <p>Idempotent by {@code featureKey}: installing the same bundle twice leaves one row, which is
   * what makes a failed install safe to retry rather than something to unpick by hand.
   *
   * @param declaration the capability to register
   * @throws com.krizaka.sovkora.jobs.api.domain.exception.CapabilityRegistrationException when the
   *     registry refused the write or could not be reached — an install that silently skipped a
   *     capability would leave blueprints that dispatch nowhere
   */
  void register(CapabilityDeclaration declaration);

  /**
   * Removes one capability row.
   *
   * <p>Exists for compensation, not for administration: when a later slice of an install fails, the
   * capabilities this one wrote have to come back out, or a half-installed pack leaves rows nothing
   * references.
   *
   * @param featureKey the capability to remove
   * @return {@code true} when a row was removed, {@code false} when there was none
   */
  boolean unregister(String featureKey);
}
