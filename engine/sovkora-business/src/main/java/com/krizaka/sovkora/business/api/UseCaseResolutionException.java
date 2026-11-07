package com.krizaka.sovkora.business.api;

/**
 * Thrown by the {@link UseCaseDispatcher} when an {@link Intention} cannot be served — no
 * registered {@link UseCase} matches it (ERR-410) or RBAC denies the actor (ERR-403). Messages
 * carry the {@code ERR-*} code.
 */
public class UseCaseResolutionException extends RuntimeException {
  public UseCaseResolutionException(String message) {
    super(message);
  }
}
