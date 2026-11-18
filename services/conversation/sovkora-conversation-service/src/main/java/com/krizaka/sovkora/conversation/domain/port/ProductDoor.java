package com.krizaka.sovkora.conversation.domain.port;

import com.krizaka.sovkora.engine.core.domain.model.security.Actor;

/**
 * What every door a product calls over HTTP asks of its caller before anything else (ADR-091): a
 * verified actor holding an allowed role, within its rate limit. The checks and the document reads
 * share it, so a door cannot be added with a weaker gate.
 */
public interface ProductDoor {

  /**
   * Whether the actor may use the door at all.
   *
   * @param actor the verified actor
   * @return {@code true} when it holds one of the allowed roles
   */
  boolean permits(Actor actor);

  /**
   * Takes one call of the actor's rate window.
   *
   * @param actor the verified actor
   * @return {@code 0} when it may go on, else the seconds to wait
   */
  long throttle(Actor actor);
}
