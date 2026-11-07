package com.krizaka.sovkora.business.api;

/**
 * SPI extension point — adding a product capability means implementing a {@code UseCase} and
 * declaring its {@link UseCaseDescriptor}. A use-case orchestrates core capabilities (via the
 * core's inbound ports) for one descriptor; it hosts coordination, never LLM intelligence.
 *
 * @param <I> The typed input payload this use-case accepts.
 * @param <R> The result type.
 */
public interface UseCase<I extends UseCasePayload, R> {

  /**
   * @return The immutable metadata describing this use-case (id, capability, RBAC, …).
   */
  UseCaseDescriptor descriptor();

  /**
   * Executes the use-case for a resolved input.
   *
   * @param ctx The execution context (actor, session, authorities, preferences).
   * @param input The typed input payload.
   * @return The result.
   */
  R execute(UseCaseContext ctx, I input);
}
