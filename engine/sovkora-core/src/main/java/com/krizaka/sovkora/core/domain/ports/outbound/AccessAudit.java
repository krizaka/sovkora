package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException.Reason;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import java.util.Optional;

/**
 * Where a refusal is recorded (ADR-091): who (the actor, when there is one), which resource and why
 * — never the arguments or the content of the call (ADR-064).
 */
public interface AccessAudit {

  /**
   * Records a refused tool call or listing.
   *
   * @param actor who asked, if known
   * @param action what was attempted, e.g. {@code tool.execute}
   * @param resource the refused resource's name
   * @param reason why
   */
  void denied(Optional<Actor> actor, String action, String resource, Reason reason);
}
