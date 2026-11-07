package com.krizaka.sovkora.adapter.springai.tool;

import com.krizaka.sovkora.core.domain.model.chat.EngineTool;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException;
import com.krizaka.sovkora.core.domain.model.security.AccessDeniedException.Reason;
import com.krizaka.sovkora.core.domain.model.security.Actor;
import com.krizaka.sovkora.core.domain.ports.outbound.PlatformToolConfigProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolPermissionProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolResultCache;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

/**
 * Spring AI implementation of the {@link ToolRegistry} port: local Java functions registered as
 * {@link ToolCallback}s, wrapped by a {@link CachingToolCallback} when the platform's tool
 * configuration asks for caching. The tools themselves are registered by whoever owns them (the
 * {@code sovkora-tools} catalogue); this class knows nothing of them.
 */
public class SpringAiToolRegistry implements ToolRegistry {

  private static final long DEFAULT_CACHE_TTL_SECONDS = 3600L;

  private final PlatformToolConfigProvider platformToolConfigProvider;
  private final ToolResultCache cache;
  private final ToolPermissionProvider permissions;

  /** Insertion order is the order the model is offered the tools in. */
  private final Map<String, Registration> registered =
      java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());

  /**
   * A tool and the roles it declared when it registered (an administrator's rows override them).
   */
  private record Registration(ToolCallback tool, Set<String> declaredRoles) {}

  /**
   * Creates the registry.
   *
   * @param platformToolConfigProvider the platform's tool configuration (optional)
   * @param cache where cached results are kept
   * @param permissions the roles an administrator granted each tool (ADR-091; optional: without it
   *     the roles a tool declared apply)
   */
  public SpringAiToolRegistry(
      PlatformToolConfigProvider platformToolConfigProvider,
      ToolResultCache cache,
      ToolPermissionProvider permissions) {
    this.platformToolConfigProvider = platformToolConfigProvider;
    this.cache = cache;
    this.permissions = permissions;
  }

  @Override
  public <I, O> void registerTool(
      String name,
      String description,
      Class<I> inputType,
      Function<I, O> function,
      Set<String> requiredRoles) {
    ToolCallback tool =
        FunctionToolCallback.builder(name, function)
            .description(description)
            .inputType(inputType)
            .build();
    this.registered.put(name, new Registration(tool, Set.copyOf(requiredRoles)));
  }

  /**
   * The tools the actor may use, bound to the actor and, where configured, cached. The cache key
   * carries the actor's scope, so an answer is never served to someone else.
   */
  @Override
  public List<EngineTool> getRegisteredTools(Actor actor) {
    if (actor == null) {
      return List.of();
    }
    List<Map.Entry<String, Registration>> entries;
    synchronized (registered) {
      entries = List.copyOf(registered.entrySet());
    }
    return entries.stream()
        .filter(entry -> actor.holdsAny(requiredRoles(entry.getKey(), entry.getValue())))
        .map(entry -> toEngine(bind(actor, entry.getValue().tool())))
        .toList();
  }

  @Override
  public EngineTool authorize(Actor actor, String name) {
    Registration registration = registered.get(name);
    if (registration == null) {
      throw new IllegalArgumentException("Tool not found: " + name);
    }
    if (actor == null) {
      throw new AccessDeniedException(name, Reason.NO_ACTOR);
    }
    if (!actor.holdsAny(requiredRoles(name, registration))) {
      throw new AccessDeniedException(name, Reason.MISSING_ROLE);
    }
    return toEngine(bind(actor, registration.tool()));
  }

  /** The configured roles when an administrator set some, else what the tool declared. */
  private Set<String> requiredRoles(String name, Registration registration) {
    return permissions == null
        ? registration.declaredRoles()
        : permissions.requiredRoles(name).orElse(registration.declaredRoles());
  }

  private ToolCallback bind(Actor actor, ToolCallback tool) {
    ToolCallback callback = tool;
    String name = tool.getToolDefinition().name();
    if (platformToolConfigProvider != null) {
      var config = platformToolConfigProvider.getToolConfig(name);
      if (config.isPresent() && Boolean.TRUE.equals(config.get().cacheEnabled())) {
        long ttl =
            config.get().cacheTtlSeconds() != null
                ? config.get().cacheTtlSeconds()
                : DEFAULT_CACHE_TTL_SECONDS;
        callback = new CachingToolCallback(tool, cache, true, ttl, actor.scope());
      }
    }
    return new ActorBoundToolCallback(actor, callback);
  }

  private static EngineTool toEngine(ToolCallback tool) {
    var definition = tool.getToolDefinition();
    return new EngineTool(
        definition.name(), definition.description(), definition.inputSchema(), tool::call, tool);
  }
}
