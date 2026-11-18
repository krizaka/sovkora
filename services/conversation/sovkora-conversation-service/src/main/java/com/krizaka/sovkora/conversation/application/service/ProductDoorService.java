package com.krizaka.sovkora.conversation.application.service;

import com.krizaka.sovkora.conversation.domain.port.ProductDoor;
import com.krizaka.sovkora.engine.core.domain.model.security.Actor;
import java.util.Objects;
import java.util.Set;

/**
 * The gate of a product door (ADR-091): one of the allowed roles, and a rate limit per actor of
 * each tenant. Extended by each door's service, so the gate is written once.
 */
public class ProductDoorService implements ProductDoor {

  private final CheckRateLimitService limiter;
  private final Set<String> allowedRoles;

  /**
   * @param limiter the per-actor rate limit
   * @param allowedRoles the roles any of which may use the door
   */
  public ProductDoorService(CheckRateLimitService limiter, Set<String> allowedRoles) {
    this.limiter = Objects.requireNonNull(limiter, "limiter must not be null");
    this.allowedRoles = Set.copyOf(allowedRoles);
  }

  @Override
  public boolean permits(Actor actor) {
    return actor.holdsAny(allowedRoles);
  }

  @Override
  public long throttle(Actor actor) {
    return limiter.tryAcquire(actor.tenant() + "/" + actor.subject());
  }
}
