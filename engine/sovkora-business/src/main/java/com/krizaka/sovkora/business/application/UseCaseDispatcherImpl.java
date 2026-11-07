package com.krizaka.sovkora.business.application;

import com.krizaka.sovkora.business.api.*;
import java.util.Objects;

/**
 * App-Factory dispatcher: resolves an {@link Intention} to its {@link UseCase} via the {@link
 * UseCaseRegistry}, enforces the descriptor's RBAC against the actor's authorities, then executes
 * the use-case with a context derived from the intention.
 */
public final class UseCaseDispatcherImpl implements UseCaseDispatcher {

  private final UseCaseRegistry registry;

  public UseCaseDispatcherImpl(UseCaseRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R dispatch(Intention intention) {
    Objects.requireNonNull(intention, "intention must not be null");
    UseCase<?, ?> useCase =
        registry
            .resolve(intention)
            .orElseThrow(
                () ->
                    new UseCaseResolutionException(
                        "ERR-410: no use-case registered for capability "
                            + intention.capability()));

    UseCaseContext ctx =
        new UseCaseContext(
            intention.id(),
            intention.context().actorId(),
            intention.context().sessionId(),
            intention.context().authorities(),
            intention.context().preferences());

    if (!useCase.descriptor().rbac().permits(ctx.authorities())) {
      throw new UseCaseResolutionException(
          "ERR-403: actor not authorized for use-case " + useCase.descriptor().id());
    }

    // The registry matched by capability, so the intention's payload is the use-case's input type.
    UseCase<Payload, R> typed = (UseCase<Payload, R>) useCase;
    return typed.execute(ctx, intention.payload());
  }
}
