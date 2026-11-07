package com.krizaka.sovkora.adapter.springai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.model.Authority;
import com.krizaka.sovkora.core.domain.model.Context;
import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution.ResolvedTools;
import com.krizaka.sovkora.core.domain.ports.outbound.UserMcpServerProvider;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

/** ADR-091 behind the port: the tools are the actor's, and a turn with no actor has none. */
class SpringAiToolResolutionTest {

  private final ToolRegistry registry = mock(ToolRegistry.class);
  private final PlatformMcpServerProvider platform = mock(PlatformMcpServerProvider.class);
  private final UserMcpServerProvider user = mock(UserMcpServerProvider.class);
  private final SpringAiToolResolution resolution =
      new SpringAiToolResolution(platform, user, registry);

  private static InternalChatRequest request(String userId, Set<Authority> authorities) {
    return new InternalChatRequest(
        "hi", List.of(), null, new Context(userId, "c-1", Map.of(), authorities), false);
  }

  @Test
  void aTurnWithoutActorGetsNoToolAndNoServerIsAsked() {
    when(registry.getRegisteredTools(null)).thenReturn(List.of());

    ResolvedTools tools = resolution.resolve(request("anonymous", Set.of()));

    assertThat(tools.handles()).isEmpty();
    assertThat(tools.closeables()).isEmpty();
    verifyNoInteractions(platform, user);
  }

  @Test
  void theRegistryToolsOfTheActorBecomeLibraryHandles() {
    Actor actor = Actor.of("user-1", List.of("ROLE_USER")).orElseThrow();
    EngineTool tool = mock(EngineTool.class);
    when(tool.name()).thenReturn("echo");
    when(tool.description()).thenReturn("echoes");
    when(tool.inputSchema()).thenReturn("{}");
    when(registry.getRegisteredTools(actor)).thenReturn(List.of(tool));
    when(platform.getActivePlatformMcpServers()).thenReturn(List.of());
    when(user.getActiveUserMcpServers("user-1")).thenReturn(List.of());

    ResolvedTools tools = resolution.resolve(request("user-1", Set.of(new Authority("ROLE_USER"))));

    assertThat(tools.handles()).hasSize(1).first().isInstanceOf(ToolCallback.class);
    assertThat(((ToolCallback) tools.handles().getFirst()).getToolDefinition().name())
        .isEqualTo("echo");
  }
}
