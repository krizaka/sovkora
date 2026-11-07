package com.krizaka.sovkora.adapter.springai.tool;

import com.krizaka.sovkora.core.application.security.ClientToolGate;
import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.chat.InternalChatRequest;
import com.krizaka.sovkora.core.domain.model.chat.ToolCall;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution;
import com.krizaka.sovkora.core.domain.ports.outbound.UserMcpServerProvider;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link ToolResolution} on Spring AI: builds the MCP client connections and resolves the tool
 * callbacks from the platform's servers, the user's own and the local tool registry (#121).
 *
 * <p>Follows ERR-107 (Mapper Isolation Invariant applied to factory patterns).
 */
public final class SpringAiToolResolution implements ToolResolution {

  private static final Logger logger = LoggerFactory.getLogger(SpringAiToolResolution.class);

  private final PlatformMcpServerProvider platformMcpServerProvider;
  private final UserMcpServerProvider userMcpServerProvider;
  private final ToolRegistry toolRegistry;
  private final ClientToolGate clientToolGate;

  /**
   * Constructs the resolution.
   *
   * @param platformMcpServerProvider The platform-wide MCP provider (nullable).
   * @param userMcpServerProvider The user private MCP provider (nullable).
   * @param toolRegistry The local tools registry (nullable).
   * @param clientToolGate The ADR-091 check of the tools the client runs (ADR-093 §4); {@code null}
   *     means client-executed mode offers nothing.
   */
  public SpringAiToolResolution(
      PlatformMcpServerProvider platformMcpServerProvider,
      UserMcpServerProvider userMcpServerProvider,
      ToolRegistry toolRegistry,
      ClientToolGate clientToolGate) {
    this.platformMcpServerProvider = platformMcpServerProvider;
    this.userMcpServerProvider = userMcpServerProvider;
    this.toolRegistry = toolRegistry;
    this.clientToolGate = clientToolGate;
  }

  /** A resolution with no client-executed mode: it offers no client tool. */
  public SpringAiToolResolution(
      PlatformMcpServerProvider platformMcpServerProvider,
      UserMcpServerProvider userMcpServerProvider,
      ToolRegistry toolRegistry) {
    this(platformMcpServerProvider, userMcpServerProvider, toolRegistry, null);
  }

  /**
   * The client's declared tools the actor may use, as definitions only: no MCP server is opened and
   * none of the engine's own tools is offered beside them (the list is closed, ADR-093 §4).
   */
  @Override
  public ResolvedTools resolveClientTools(InternalChatRequest request) {
    if (clientToolGate == null) {
      return ResolvedTools.none();
    }
    Actor actor = Actor.from(request.context()).orElse(null);
    List<ClientToolCallback> callbacks =
        clientToolGate.offer(actor, request.clientTools()).stream()
            .map(ClientToolCallback::new)
            .toList();
    return new ResolvedTools(callbacks, List.of());
  }

  @Override
  public List<ToolCall> screenClientCalls(InternalChatRequest request, List<ToolCall> calls) {
    if (clientToolGate == null) {
      return List.of();
    }
    Actor actor = Actor.from(request.context()).orElse(null);
    return clientToolGate.screen(actor, request.clientTools(), calls);
  }

  @Override
  public ResolvedTools resolve(InternalChatRequest request) {
    List<AutoCloseable> closeables = new ArrayList<>();
    List<ToolCallback> callbacks = resolveToolCallbacks(request, closeables);
    return new ResolvedTools(callbacks, closeables);
  }

  /** Describes an MCP server connection for client initialization. */
  record McpServerDescriptor(
      String label,
      String url,
      String authToken,
      String transportType,
      String command,
      String args) {

    static McpServerDescriptor fromPlatform(PlatformMcpServerProvider.PlatformMcpServer server) {
      return new McpServerDescriptor(
          server.label(),
          server.url(),
          server.authToken(),
          server.transportType(),
          server.command(),
          server.args());
    }

    static McpServerDescriptor fromUser(UserMcpServerProvider.UserMcpServer server) {
      return new McpServerDescriptor(
          server.label(), server.url(), server.authToken(), "SSE", null, null);
    }
  }

  /**
   * Resolves all MCP tool callbacks from platform servers, user servers, and local registry.
   *
   * @param request The chat request (used to extract user context for user MCP servers).
   * @param closeables Mutable list to track closeable MCP clients for cleanup.
   * @return Combined list of all tool callbacks.
   */
  private List<ToolCallback> resolveToolCallbacks(
      InternalChatRequest request, List<AutoCloseable> closeables) {

    List<ToolCallback> allToolCallbacks = new ArrayList<>();

    // Tools are attached for the turn's actor only; with no actor there are none (ADR-091).
    Actor actor = Actor.from(request.context()).orElse(null);
    if (toolRegistry != null) {
      toolRegistry.getRegisteredTools(actor).stream()
          .map(SpringAiToolResolution::toCallback)
          .forEach(allToolCallbacks::add);
    }

    // A turn with no verified actor gets no MCP server: the platform's were configured for the
    // signed-in users, and a user's own servers are theirs alone.
    if (actor != null) {
      connectEnabled(
          platformServers().map(McpServerDescriptor::fromPlatform), closeables, allToolCallbacks);
      connectEnabled(
          userServers(request.context().userId()).map(McpServerDescriptor::fromUser),
          closeables,
          allToolCallbacks);
    }
    return allToolCallbacks;
  }

  private Stream<PlatformMcpServerProvider.PlatformMcpServer> platformServers() {
    if (platformMcpServerProvider == null) {
      return Stream.empty();
    }
    return platformMcpServerProvider.getActivePlatformMcpServers().stream()
        .filter(server -> Boolean.TRUE.equals(server.enabled()));
  }

  private Stream<UserMcpServerProvider.UserMcpServer> userServers(String userId) {
    if (userMcpServerProvider == null) {
      return Stream.empty();
    }
    return userMcpServerProvider.getActiveUserMcpServers(userId).stream()
        .filter(server -> Boolean.TRUE.equals(server.enabled()));
  }

  private static void connectEnabled(
      Stream<McpServerDescriptor> descriptors,
      List<AutoCloseable> closeables,
      List<ToolCallback> callbacks) {
    descriptors.forEach(d -> buildClientSafely(d, closeables, callbacks));
  }

  /** The library tool of a registered one: the original where there is one, else built from it. */
  private static ToolCallback toCallback(EngineTool tool) {
    if (tool.nativeTool() instanceof ToolCallback original) {
      return original;
    }
    return new EngineToolCallback(
        tool,
        ToolDefinition.builder()
            .name(tool.name())
            .description(tool.description())
            .inputSchema(tool.inputSchema())
            .build());
  }

  /** A registered tool that came with no library behind it, offered to the models as one. */
  private record EngineToolCallback(EngineTool tool, ToolDefinition definition)
      implements ToolCallback {

    @Override
    public ToolDefinition getToolDefinition() {
      return definition;
    }

    @Override
    public String call(String toolInput) {
      return tool.call(toolInput);
    }
  }

  /**
   * Safely initializes an MCP client and registers its tool callbacks. Failures are logged and
   * silently skipped (resilient to server downtime).
   */
  private static void buildClientSafely(
      McpServerDescriptor descriptor,
      List<AutoCloseable> closeables,
      List<ToolCallback> callbacks) {
    try {
      McpSyncClient client =
          buildClient(
              descriptor.url(),
              descriptor.authToken(),
              descriptor.transportType(),
              descriptor.command(),
              descriptor.args());
      closeables.add(client);
      callbacks.addAll(SyncMcpToolCallbackProvider.syncToolCallbacks(List.of(client)));
      logger.info("Successfully initialized MCP client: {}", descriptor.label());
    } catch (Exception e) {
      logger.error("Failed to initialize MCP client: {}", descriptor.label(), e);
    }
  }

  /**
   * Unified MCP client builder supporting both LOCAL (Stdio) and SSE transports.
   *
   * @param url The SSE server URL (used for SSE transport).
   * @param authToken Optional bearer token for SSE transport authentication.
   * @param transportType Transport type: "LOCAL" for Stdio, anything else for SSE.
   * @param command The command for LOCAL transport (e.g. "npx").
   * @param args Comma-separated arguments for LOCAL transport.
   * @return An initialized {@link McpSyncClient}.
   */
  private static McpSyncClient buildClient(
      String url, String authToken, String transportType, String command, String args) {
    McpClientTransport transport;
    if ("LOCAL".equalsIgnoreCase(transportType)) {
      List<String> argsList = new ArrayList<>();
      if (args != null && !args.isBlank()) {
        for (String arg : args.split(",")) {
          argsList.add(arg.trim());
        }
      }
      McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new JsonMapper());
      ServerParameters params = ServerParameters.builder(command).args(argsList).build();
      transport = new StdioClientTransport(params, jsonMapper);
    } else {
      HttpClientSseClientTransport.Builder transportBuilder =
          HttpClientSseClientTransport.builder(url);
      if (authToken != null && !authToken.isBlank()) {
        // MCP SDK 2.0: customizeRequest(Consumer) →
        // httpRequestCustomizer(McpSyncHttpClientRequestCustomizer).
        transportBuilder.httpRequestCustomizer(
            (requestBuilder, method, uri, body, context) ->
                requestBuilder.header("Authorization", authToken));
      }
      transport = transportBuilder.build();
    }
    McpSyncClient client = McpClient.sync(transport).build();
    client.initialize();
    return client;
  }
}
