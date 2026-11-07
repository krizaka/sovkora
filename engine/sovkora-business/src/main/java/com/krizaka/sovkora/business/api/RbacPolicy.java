package com.krizaka.sovkora.business.api;

import java.util.Set;

/**
 * RBAC requirement a {@link UseCaseDescriptor} declares: the authorities an actor must hold to run
 * the use-case.
 *
 * @param requiredAuthorities Authorities the actor must hold (empty = permit all); never null.
 */
public record RbacPolicy(Set<String> requiredAuthorities) {

  /** Policy that permits any actor. */
  public static final RbacPolicy PERMIT_ALL = new RbacPolicy(Set.of());

  public RbacPolicy {
    requiredAuthorities = requiredAuthorities == null ? Set.of() : Set.copyOf(requiredAuthorities);
  }

  /**
   * Whether an actor holding {@code actorAuthorities} satisfies this policy.
   *
   * @param actorAuthorities The actor's granted authorities (nullable).
   * @return {@code true} if no authority is required or the actor holds all required ones.
   */
  public boolean permits(Set<String> actorAuthorities) {
    return requiredAuthorities.isEmpty()
        || (actorAuthorities != null && actorAuthorities.containsAll(requiredAuthorities));
  }
}
