package com.krizaka.sovkora.business.api;

import java.util.Map;
import java.util.Set;

/**
 * Ambient context of an {@link Intention}: the conversation session, the acting principal (opaque
 * {@code actorId} — no cross-context identity coupling), the actor's resolved authorities (for
 * RBAC), and resolved preferences / environment signals.
 *
 * @param sessionId Conversation/session id (may be null for one-shot intentions).
 * @param actorId Opaque actor reference resolved by the router from the security context.
 * @param authorities The actor's granted authorities; never null.
 * @param preferences Resolved user preferences and environment signals; never null.
 */
public record IntentionContext(
    String sessionId, String actorId, Set<String> authorities, Map<String, Object> preferences) {
  public IntentionContext {
    authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    preferences = preferences == null ? Map.of() : Map.copyOf(preferences);
  }
}
