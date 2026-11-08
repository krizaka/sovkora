package com.krizaka.sovkora.jobs.api.domain.port;

import com.krizaka.sovkora.jobs.api.domain.model.CapabilityRoute;
import java.util.Optional;

/**
 * Read side of the job-plane contract: where does this capability's work run?
 *
 * <p>Consumed by every producer that publishes onto {@code orazaka.jobs} — today the Studio saga,
 * tomorrow anything dispatching a capability a pack brought with it. The mapping lives in one
 * place, {@code orazaka_capabilities}, so a producer never carries a second copy of it.
 *
 * <p><b>Does not degrade.</b> Unlike {@code PackPricingClient}, where an unknown price renders "—",
 * an unresolved route has no acceptable rendering: a step with no route cannot be dispatched
 * anywhere, and picking a queue anyway is the silent misroute this port exists to end. An
 * implementation that cannot answer returns {@link Optional#empty()} and the caller raises {@link
 * com.krizaka.sovkora.jobs.api.domain.exception.UnroutableCapabilityException} — the user gets an
 * error instead of a wrong answer.
 */
public interface CapabilityRoutingClient {

  /**
   * Resolves where a capability's work is executed.
   *
   * @param featureKey the capability key from the registry
   * @return its route, or empty when no <b>enabled</b> row carries one — never a default
   */
  Optional<CapabilityRoute> route(String featureKey);
}
