package com.krizaka.sovkora.core.application.security;

import com.krizaka.sovkora.core.domain.model.chat.ClientTool;
import com.krizaka.sovkora.core.domain.model.chat.ToolCall;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException.Reason;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.AccessAudit;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolPermissionProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The ADR-091 check for tools the client runs (ADR-093 §4): a tool is offered to the model only
 * when the turn's actor holds one of its roles, and a call the model makes is returned to the
 * client only when its tool was offered and the actor still holds the role. Both run on <b>every
 * turn</b>, from the actor the host declared and the roles as they are now; every refusal is
 * audited (the actor, the tool and the reason, never the arguments, ADR-064).
 *
 * <p>The roles of a client tool are data like the others: the administrator's row when there is
 * one, else administrators only ({@link ToolRegistry#LEAST_PRIVILEGE}). A tool the client invents
 * is therefore never offered to anybody who was not granted it.
 */
public final class ClientToolGate {

  /** What is audited when a tool is left out of the offer. */
  private static final String OFFER = "tool.offer";

  /** What is audited when a call of the model is not returned. */
  private static final String CALL = "tool.call";

  private final ToolPermissionProvider permissions;
  private final AccessAudit audit;

  /**
   * Creates the gate.
   *
   * @param permissions the roles an administrator granted each tool; {@code null} means none are
   *     configured, so every client tool is for administrators only
   * @param audit where a refusal is recorded
   */
  public ClientToolGate(ToolPermissionProvider permissions, AccessAudit audit) {
    this.permissions = permissions;
    this.audit = Objects.requireNonNull(audit, "AccessAudit must not be null");
  }

  /**
   * The tools the model is offered.
   *
   * @param actor who the turn is for; {@code null} gets none
   * @param requested what the client declares
   * @return the declared tools the actor may use, in the client's order
   */
  public List<ClientTool> offer(Actor actor, List<ClientTool> requested) {
    List<ClientTool> offered = new ArrayList<>();
    for (ClientTool tool : requested) {
      Reason refusal = refusal(actor, tool.name());
      if (refusal == null) {
        offered.add(tool);
      } else {
        audit.denied(Optional.ofNullable(actor), OFFER, tool.name(), refusal);
      }
    }
    return offered;
  }

  /**
   * The calls of the model that go back to the client.
   *
   * @param actor who the turn is for
   * @param requested what the client declared this turn
   * @param calls what the model asked for
   * @return the calls whose tool was declared and is still permitted; the others are dropped and
   *     audited
   */
  public List<ToolCall> screen(Actor actor, List<ClientTool> requested, List<ToolCall> calls) {
    Set<String> declared =
        Set.copyOf(
            requested.stream().map(ClientTool::name).collect(java.util.stream.Collectors.toSet()));
    List<ToolCall> allowed = new ArrayList<>();
    for (ToolCall call : calls) {
      Reason refusal =
          declared.contains(call.name()) ? refusal(actor, call.name()) : Reason.NOT_OFFERED;
      if (refusal == null) {
        allowed.add(call);
      } else {
        audit.denied(Optional.ofNullable(actor), CALL, call.name(), refusal);
      }
    }
    return allowed;
  }

  private Reason refusal(Actor actor, String tool) {
    if (actor == null) {
      return Reason.NO_ACTOR;
    }
    Set<String> required =
        permissions == null
            ? ToolRegistry.LEAST_PRIVILEGE
            : permissions.requiredRoles(tool).orElse(ToolRegistry.LEAST_PRIVILEGE);
    return actor.holdsAny(required) ? null : Reason.MISSING_ROLE;
  }
}
