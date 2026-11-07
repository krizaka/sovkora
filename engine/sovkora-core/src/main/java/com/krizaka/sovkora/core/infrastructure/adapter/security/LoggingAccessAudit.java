package com.krizaka.sovkora.core.infrastructure.adapter.security;

import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException.Reason;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.AccessAudit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes a refusal as one structured line on this class's logger: tenant, subject, action, resource
 * and reason. A host that keeps audit lines in a store replaces this bean (registered by the
 * adapter's auto-configuration, only when the host has none).
 */
public class LoggingAccessAudit implements AccessAudit {

  private static final Logger audit = LoggerFactory.getLogger(LoggingAccessAudit.class);

  @Override
  public void denied(Optional<Actor> actor, String action, String resource, Reason reason) {
    audit.warn(
        "access denied tenant={} subject={} action={} resource={} reason={}",
        actor.map(Actor::tenant).orElse("-"),
        actor.map(Actor::subject).orElse("-"),
        action,
        resource,
        reason);
  }
}
