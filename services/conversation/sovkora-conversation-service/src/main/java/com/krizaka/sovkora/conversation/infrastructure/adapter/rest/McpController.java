package com.krizaka.sovkora.conversation.infrastructure.adapter.rest;

import com.krizaka.sovkora.engine.core.domain.model.security.AccessDeniedException;
import com.krizaka.sovkora.engine.core.domain.model.security.Actor;
import com.krizaka.sovkora.engine.core.domain.ports.inbound.McpService;
import com.krizaka.sovkora.engine.core.domain.ports.outbound.McpServerPolicy;
import com.krizaka.sovkora.persistence.api.domain.model.PlatformMcpServerDto;
import com.krizaka.sovkora.persistence.api.domain.model.UserMcpServerDto;
import com.krizaka.sovkora.persistence.api.domain.ports.inbound.PlatformMcpServerPersistenceProvider;
import com.krizaka.sovkora.persistence.api.domain.ports.inbound.UserMcpServerPersistenceProvider;
import com.krizaka.users.domain.model.User;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** REST Controller for inspecting, registering, and managing platform and user MCP servers. */
@RestController
@RequestMapping("/api/v1/mcp")
public class McpController {

  private static final Logger logger = LoggerFactory.getLogger(McpController.class);

  /**
   * What an answer shows instead of a stored token: that one is set, never the token itself
   * (sovkora-engine#58). Sent back unchanged on an edit, it means « keep the stored one ».
   */
  static final String TOKEN_MASK = "********";

  /** The transport of a platform server the engine starts as a local process (stdio). */
  private static final String LOCAL_TRANSPORT = "LOCAL";

  private final McpService mcpService;
  private final PlatformMcpServerPersistenceProvider platformMcpServerPersistenceProvider;
  private final UserMcpServerPersistenceProvider userMcpServerPersistenceProvider;
  private final ObjectMapper objectMapper;
  private final McpServerPolicy mcpServerPolicy;

  /**
   * Constructs the McpController.
   *
   * @param mcpService The MCP service.
   * @param platformMcpServerPersistenceProvider Platform MCP persistence provider.
   * @param userMcpServerPersistenceProvider User MCP persistence provider.
   * @param objectMapper Spring-managed JSON ObjectMapper.
   * @param mcpServerPolicy Which MCP servers may be registered: public https endpoints, and local
   *     servers only by an id the deployment declares.
   */
  public McpController(
      McpService mcpService,
      PlatformMcpServerPersistenceProvider platformMcpServerPersistenceProvider,
      UserMcpServerPersistenceProvider userMcpServerPersistenceProvider,
      ObjectMapper objectMapper,
      McpServerPolicy mcpServerPolicy) {
    this.mcpService = Objects.requireNonNull(mcpService, "McpService must not be null");
    this.platformMcpServerPersistenceProvider =
        Objects.requireNonNull(
            platformMcpServerPersistenceProvider,
            "PlatformMcpServerPersistenceProvider must not be null");
    this.userMcpServerPersistenceProvider =
        Objects.requireNonNull(
            userMcpServerPersistenceProvider, "UserMcpServerPersistenceProvider must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    this.mcpServerPolicy =
        Objects.requireNonNull(mcpServerPolicy, "McpServerPolicy must not be null");
  }

  /**
   * Retrieves the registered tools the caller may use, with their schemas. A tool the caller may
   * not use is absent, not merely refused (ADR-091).
   *
   * @param user The authenticated caller; the actor is built from the session, never from the
   *     request.
   * @return A list of ToolInfoDto objects.
   */
  @GetMapping("/tools")
  public ResponseEntity<@NotNull List<ToolInfoDto>> getTools(@AuthenticationPrincipal User user) {
    List<ToolInfoDto> tools =
        mcpService.getAvailableTools(actorOf(user)).stream()
            .map(tool -> new ToolInfoDto(tool.name(), tool.description(), tool.inputSchema()))
            .toList();
    return ResponseEntity.ok(tools);
  }

  /**
   * Executes a tool by name with the provided arguments, for the authenticated caller: a tool the
   * caller may not use answers 403 and leaves an audit line (ADR-091).
   *
   * @param name The name of the tool.
   * @param payload The arguments map.
   * @param user The authenticated caller.
   * @return A response entity containing the result or error.
   */
  @PostMapping("/tools/{name}/execute")
  public ResponseEntity<@NotNull Map<String, Object>> executeTool(
      @PathVariable String name,
      @RequestBody Map<String, Object> payload,
      @AuthenticationPrincipal User user) {
    try {
      String jsonInput = objectMapper.writeValueAsString(payload);
      String result = mcpService.executeTool(actorOf(user), name, jsonInput);
      return ResponseEntity.ok(Map.of("result", result));
    } catch (AccessDeniedException e) {
      // The reason is in the audit line; the answer says only that it is refused.
      logger.warn("Access denied for MCP tool execution: {}", name, e);
      return ResponseEntity.status(403).build();
    } catch (IllegalArgumentException e) {
      logger.warn("Invalid argument for MCP tool {}: {}", name, e.getMessage(), e);
      return ResponseEntity.notFound().build();
    } catch (JacksonException e) {
      logger.warn("JSON parsing error for MCP tool {}: {}", name, e.getMessage(), e);
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Invalid payload: " + e.getMessage()));
    }
  }

  /** The actor of the session: none for a caller that is not a signed-in user. */
  private static Actor actorOf(User user) {
    return user == null ? null : Actor.of(user.id().toString(), user.authorities()).orElse(null);
  }

  @GetMapping("/servers/user")
  public ResponseEntity<@NotNull List<UserMcpServerDto>> getUserServers(
      @AuthenticationPrincipal User user) {
    if (user == null) {
      return ResponseEntity.status(401).build();
    }
    return ResponseEntity.ok(
        userMcpServerPersistenceProvider.findByUserId(user.id().toString()).stream()
            .map(McpController::masked)
            .toList());
  }

  @PostMapping("/servers/user")
  public ResponseEntity<@NotNull Object> saveUserServer(
      @RequestBody UserMcpServerDto payload, @AuthenticationPrincipal User user) {
    if (user == null) {
      return ResponseEntity.status(401).build();
    }
    if (!user.id().toString().equals(payload.userId())) {
      return ResponseEntity.badRequest().body("User ID mismatch");
    }
    if (payload.url() == null || payload.url().isBlank()) {
      return ResponseEntity.badRequest().body("URL cannot be empty");
    }
    Optional<McpServerPolicy.Refusal> refusal = mcpServerPolicy.refusal(payload.url());
    if (refusal.isPresent()) {
      return ResponseEntity.badRequest().body(refusedUrlMessage(refusal.get()));
    }
    // An id already held by another user is not ours to overwrite (an upsert is a takeover).
    UserMcpServerDto toSave = payload;
    if (payload.id() != null) {
      var existing = userMcpServerPersistenceProvider.findById(payload.id());
      if (existing.isPresent() && !user.id().toString().equals(existing.get().userId())) {
        return ResponseEntity.notFound().build();
      }
      if (existing.isPresent() && keepsStoredToken(payload.authToken())) {
        toSave =
            new UserMcpServerDto(
                payload.id(),
                payload.userId(),
                payload.label(),
                payload.url(),
                existing.get().authToken(),
                payload.enabled(),
                payload.createdAt());
      }
    }
    UserMcpServerDto saved = userMcpServerPersistenceProvider.save(toSave);
    return ResponseEntity.ok(masked(saved));
  }

  @DeleteMapping("/servers/user/{id}")
  public ResponseEntity<@NotNull Object> deleteUserServer(
      @PathVariable Integer id, @AuthenticationPrincipal User user) {
    if (user == null) {
      return ResponseEntity.status(401).build();
    }
    var existingOpt = userMcpServerPersistenceProvider.findById(id);
    if (existingOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    if (!user.id().toString().equals(existingOpt.get().userId())) {
      // 404, not 403: a foreign id looks exactly like a missing one (#17).
      return ResponseEntity.notFound().build();
    }
    userMcpServerPersistenceProvider.deleteById(id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/servers/platform")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ResponseEntity<@NotNull List<PlatformMcpServerDto>> getPlatformServers() {
    return ResponseEntity.ok(
        platformMcpServerPersistenceProvider.findAll().stream()
            .map(McpController::masked)
            .toList());
  }

  @PostMapping("/servers/platform")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ResponseEntity<@NotNull Object> savePlatformServer(
      @RequestBody PlatformMcpServerDto payload) {
    Optional<String> refused = platformRefusal(payload);
    if (refused.isPresent()) {
      return ResponseEntity.badRequest().body(refused.get());
    }
    PlatformMcpServerDto toSave = payload;
    if (payload.id() != null && keepsStoredToken(payload.authToken())) {
      var existing = platformMcpServerPersistenceProvider.findById(payload.id());
      if (existing.isPresent()) {
        toSave =
            new PlatformMcpServerDto(
                payload.id(),
                payload.label(),
                payload.transportType(),
                payload.url(),
                payload.command(),
                payload.args(),
                existing.get().authToken(),
                payload.enabled(),
                payload.createdAt());
      }
    }
    PlatformMcpServerDto saved = platformMcpServerPersistenceProvider.save(toSave);
    return ResponseEntity.ok(masked(saved));
  }

  @DeleteMapping("/servers/platform/{id}")
  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  public ResponseEntity<@NotNull Void> deletePlatformServer(@PathVariable Integer id) {
    platformMcpServerPersistenceProvider.deleteById(id);
    return ResponseEntity.ok().build();
  }

  /**
   * Why a platform server may not be registered. A local server is an id the deployment declares,
   * with its command and arguments: the request names which one, never what to run. Any other is an
   * endpoint the policy must admit.
   */
  private Optional<String> platformRefusal(PlatformMcpServerDto payload) {
    if (payload.url() == null && payload.command() == null) {
      return Optional.of("Either URL or Command must be provided");
    }
    if (!LOCAL_TRANSPORT.equalsIgnoreCase(payload.transportType())) {
      return mcpServerPolicy.refusal(payload.url()).map(McpController::refusedUrlMessage);
    }
    if (payload.args() != null && !payload.args().isBlank()) {
      return Optional.of("A local MCP server takes its arguments from the configuration");
    }
    return mcpServerPolicy.stdioLaunch(payload.command()).isEmpty()
        ? Optional.of("A local MCP server must name a server declared in the configuration")
        : Optional.empty();
  }

  /** The answer to an endpoint the policy refuses: the reason, never the address it resolved to. */
  private static String refusedUrlMessage(McpServerPolicy.Refusal refusal) {
    return "MCP server URL refused (" + refusal + "): an https URL on a public host is required";
  }

  private static boolean keepsStoredToken(String sent) {
    return sent == null || sent.isBlank() || TOKEN_MASK.equals(sent);
  }

  private static UserMcpServerDto masked(UserMcpServerDto dto) {
    return dto == null
        ? null
        : new UserMcpServerDto(
            dto.id(),
            dto.userId(),
            dto.label(),
            dto.url(),
            mask(dto.authToken()),
            dto.enabled(),
            dto.createdAt());
  }

  private static PlatformMcpServerDto masked(PlatformMcpServerDto dto) {
    return dto == null
        ? null
        : new PlatformMcpServerDto(
            dto.id(),
            dto.label(),
            dto.transportType(),
            dto.url(),
            dto.command(),
            dto.args(),
            mask(dto.authToken()),
            dto.enabled(),
            dto.createdAt());
  }

  private static String mask(String token) {
    return token == null || token.isBlank() ? null : TOKEN_MASK;
  }

  public static record ToolInfoDto(String name, String description, String inputSchema) {
    public ToolInfoDto {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(description, "description must not be null");
      Objects.requireNonNull(inputSchema, "inputSchema must not be null");
    }
  }
}
