package com.krizaka.sovkora.engine.springai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.engine.core.domain.model.Authority;
import com.krizaka.sovkora.engine.core.domain.model.Context;
import com.krizaka.sovkora.engine.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.engine.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.engine.core.domain.model.security.Actor;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.McpServerPolicy;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.PlatformMcpServerProvider.PlatformMcpServer;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.ToolResolution.ResolvedTools;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.UserMcpServerProvider;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
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

  /** The policy of the tests: one public host, one declared local server ({@code filesystem}). */
  private static final class RecordingPolicy implements McpServerPolicy {
    private final List<String> askedUrls = new ArrayList<>();
    private final List<String> askedIds = new ArrayList<>();

    @Override
    public Optional<Refusal> refusal(String url) {
      askedUrls.add(url);
      return url.startsWith("https://mcp.example/")
          ? Optional.empty()
          : Optional.of(Refusal.ADDRESS);
    }

    @Override
    public Optional<StdioLaunch> stdioLaunch(String id) {
      askedIds.add(id);
      return "filesystem".equals(id)
          ? Optional.of(new StdioLaunch("npx", List.of("-y", "mcp-server-filesystem")))
          : Optional.empty();
    }
  }

  private static final Set<Authority> USER_ROLE = Set.of(new Authority("ROLE_USER"));

  /** The transports the engine tried to open in one turn, with every open stubbed. */
  private List<McpClientTransport> transportsOpened(
      SpringAiToolResolution tools, List<PlatformMcpServer> platformServers, String userUrl) {
    Actor actor = Actor.of("user-1", List.of("ROLE_USER")).orElseThrow();
    when(registry.getRegisteredTools(actor)).thenReturn(List.of());
    when(platform.getActivePlatformMcpServers()).thenReturn(platformServers);
    when(user.getActiveUserMcpServers("user-1"))
        .thenReturn(
            userUrl == null
                ? List.of()
                : List.of(
                    new UserMcpServerProvider.UserMcpServer(
                        1, "user-1", "u", userUrl, null, true)));
    List<McpClientTransport> opened = new ArrayList<>();
    McpClient.SyncSpec spec = mock(McpClient.SyncSpec.class);
    when(spec.build()).thenReturn(mock(McpSyncClient.class));
    try (MockedStatic<McpClient> clients = mockStatic(McpClient.class);
        MockedStatic<SyncMcpToolCallbackProvider> callbacks =
            mockStatic(SyncMcpToolCallbackProvider.class)) {
      clients
          .when(() -> McpClient.sync(any(McpClientTransport.class)))
          .thenAnswer(
              call -> {
                opened.add(call.getArgument(0));
                return spec;
              });
      callbacks
          .when(() -> SyncMcpToolCallbackProvider.syncToolCallbacks(any()))
          .thenReturn(List.of());
      tools.resolve(request("user-1", USER_ROLE));
    }
    return opened;
  }

  private static PlatformMcpServer local(String command, String args) {
    return new PlatformMcpServer(2, "local", "LOCAL", null, command, args, null, true);
  }

  private static PlatformMcpServer remote(String url) {
    return new PlatformMcpServer(1, "remote", "SSE", url, null, null, null, true);
  }

  @Test
  void aServerTheUserStoredOnTheInsideIsNeverOpened() {
    var policy = new RecordingPolicy();
    var tools = new SpringAiToolResolution(platform, user, registry, null, policy);

    var opened = transportsOpened(tools, List.of(), "https://inside.example/sse");

    assertThat(opened).isEmpty();
    assertThat(policy.askedUrls).containsExactly("https://inside.example/sse");
  }

  @Test
  void aPlatformServerOnTheInsideIsNeverOpened() {
    var tools = new SpringAiToolResolution(platform, user, registry, null, new RecordingPolicy());

    assertThat(transportsOpened(tools, List.of(remote("https://10.0.0.5/sse")), null)).isEmpty();
  }

  @Test
  void aPublicHttpsServerIsOpened() {
    var tools = new SpringAiToolResolution(platform, user, registry, null, new RecordingPolicy());

    assertThat(transportsOpened(tools, List.of(), "https://mcp.example/sse")).hasSize(1);
  }

  @Test
  void aLocalServerWithACommandNotDeclaredStartsNoProcess() {
    var policy = new RecordingPolicy();
    var tools = new SpringAiToolResolution(platform, user, registry, null, policy);

    var opened = transportsOpened(tools, List.of(local("/bin/sh", "-c,id")), null);

    assertThat(opened).isEmpty();
    assertThat(policy.askedIds).containsExactly("/bin/sh");
  }

  @Test
  void aLocalServerNamingADeclaredIdIsStarted() {
    var tools = new SpringAiToolResolution(platform, user, registry, null, new RecordingPolicy());

    var opened = transportsOpened(tools, List.of(local("filesystem", "-e,ignored")), null);

    assertThat(opened).singleElement().isInstanceOf(StdioClientTransport.class);
  }

  @Test
  void withNoPolicyWiredNoMcpServerIsOpened() {
    var tools = new SpringAiToolResolution(platform, user, registry);

    var opened =
        transportsOpened(
            tools, List.of(remote("https://mcp.example/sse"), local("filesystem", null)), null);

    assertThat(opened).isEmpty();
  }
}
