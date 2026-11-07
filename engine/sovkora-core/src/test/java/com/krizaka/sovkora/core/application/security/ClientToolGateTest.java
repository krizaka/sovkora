package com.krizaka.sovkora.core.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.model.chat.ClientTool;
import com.krizaka.sovkora.core.domain.model.chat.ToolCall;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException.Reason;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.AccessAudit;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolPermissionProvider;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-091 for the tools the client runs (ADR-093 §4): roles on the offer and on the calls. */
class ClientToolGateTest {

  private static final Actor DEV = Actor.of("dev-1", List.of("ROLE_DEV")).orElseThrow();
  private static final ClientTool READ = new ClientTool("read_file", "reads", "{}");
  private static final ClientTool DIFF = new ClientTool("propose_diff", "proposes", "{}");
  private static final ClientTool SHELL = new ClientTool("shell", "runs anything", "{}");

  private final ToolPermissionProvider permissions = mock(ToolPermissionProvider.class);
  private final AccessAudit audit = mock(AccessAudit.class);
  private final ClientToolGate gate = new ClientToolGate(permissions, audit);

  private void grant(String tool, String... roles) {
    when(permissions.requiredRoles(tool)).thenReturn(Optional.of(Set.of(roles)));
  }

  @Test
  @DisplayName(
      "a tool is offered only to an actor holding one of its roles; the others are audited")
  void offersWhatTheRolesAllow() {
    grant("read_file", "ROLE_DEV");
    grant("propose_diff", "ROLE_DEV");
    grant("shell", "ROLE_ADMIN");
    when(permissions.requiredRoles("unknown")).thenReturn(Optional.empty());

    assertThat(gate.offer(DEV, List.of(READ, SHELL, DIFF))).containsExactly(READ, DIFF);

    verify(audit).denied(Optional.of(DEV), "tool.offer", "shell", Reason.MISSING_ROLE);
  }

  @Test
  @DisplayName("a tool nobody configured is for administrators only: the client cannot invent one")
  void anUnconfiguredToolIsLeastPrivilege() {
    when(permissions.requiredRoles(any())).thenReturn(Optional.empty());

    assertThat(gate.offer(DEV, List.of(READ))).isEmpty();
    verify(audit).denied(Optional.of(DEV), "tool.offer", "read_file", Reason.MISSING_ROLE);

    var admin = Actor.of("root", List.of("ROLE_ADMIN")).orElseThrow();
    assertThat(gate.offer(admin, List.of(READ))).containsExactly(READ);
  }

  @Test
  @DisplayName(
      "without a verified actor nothing is offered, and without a provider nobody is trusted")
  void noActorNoTools() {
    assertThat(gate.offer(null, List.of(READ))).isEmpty();
    verify(audit).denied(Optional.empty(), "tool.offer", "read_file", Reason.NO_ACTOR);

    var closed = new ClientToolGate(null, audit);
    assertThat(closed.offer(DEV, List.of(READ))).isEmpty();
  }

  @Test
  @DisplayName(
      "a call to a tool that was not declared is dropped and audited, even if the actor could use it")
  void aCallToAnUndeclaredToolIsDropped() {
    grant("read_file", "ROLE_DEV");
    grant("shell", "ROLE_DEV");

    List<ToolCall> back =
        gate.screen(
            DEV,
            List.of(READ),
            List.of(new ToolCall("1", "read_file", "{}"), new ToolCall("2", "shell", "{}")));

    assertThat(back).extracting(ToolCall::name).containsExactly("read_file");
    verify(audit).denied(Optional.of(DEV), "tool.call", "shell", Reason.NOT_OFFERED);
  }

  @Test
  @DisplayName("a role revoked between the offer and the answer closes the call")
  void aRevokedRoleClosesTheCall() {
    grant("read_file", "ROLE_OTHER");

    assertThat(gate.screen(DEV, List.of(READ), List.of(new ToolCall("1", "read_file", "{}"))))
        .isEmpty();
    verify(audit).denied(Optional.of(DEV), "tool.call", "read_file", Reason.MISSING_ROLE);
  }

  @Test
  @DisplayName("an allowed call leaves no audit line")
  void allowedIsNotAudited() {
    grant("read_file", "ROLE_DEV");
    gate.screen(DEV, List.of(READ), List.of(new ToolCall("1", "read_file", "{}")));
    verify(audit, never()).denied(any(), any(), any(), any());
  }
}
