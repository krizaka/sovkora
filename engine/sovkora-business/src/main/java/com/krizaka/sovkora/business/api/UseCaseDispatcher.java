package com.krizaka.sovkora.business.api;

/**
 * Inbound port — the single entry the router calls after translating transport into an {@link
 * Intention}. Resolves the intention to a {@link UseCase} via the {@link UseCaseRegistry}, enforces
 * RBAC, and runs it.
 *
 * <p>Only synchronous {@link #dispatch} is offered today; the streaming (SSE relay) and async (job
 * submit) entry points are added when the router ingress (Phase 5) and messaging (Phase 6) are
 * wired in.
 */
public interface UseCaseDispatcher {

  /**
   * Resolves and executes the intention's use-case, returning its result.
   *
   * @param intention The intention to dispatch.
   * @param <R> The result type.
   * @return The use-case result.
   * @throws UseCaseResolutionException if no use-case serves the intention or RBAC denies it.
   */
  <R> R dispatch(Intention intention);
}
