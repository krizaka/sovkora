package com.krizaka.sovkora.business.api;

import java.util.Map;
import java.util.Set;

/**
 * Execution context handed to a {@link UseCase}, derived by the dispatcher from the {@link
 * Intention} and the resolved security context.
 *
 * @param intentionId Correlation id of the originating intention.
 * @param actorId Opaque acting principal reference.
 * @param sessionId Conversation/session id (may be null).
 * @param authorities The actor's granted authorities (for RBAC); never null.
 * @param preferences Resolved preferences / environment signals; never null.
 */
public record UseCaseContext(
    String intentionId,
    String actorId,
    String sessionId,
    Set<String> authorities,
    Map<String, Object> preferences) {
  public UseCaseContext {
    authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    preferences = preferences == null ? Map.of() : Map.copyOf(preferences);
  }
}
