package com.krizaka.sovkora.adapter.springai.tool;

import com.krizaka.sovkora.core.domain.ports.outbound.ToolResultCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * A caching decorator for the Spring AI {@link ToolCallback} SPI.
 *
 * <p>Lives in the Spring AI adapter (ADR-092 §4): it implements a framework SPI and is instantiated
 * by {@link SpringAiToolRegistry}. The caching policy itself lives in {@link ToolCacheStore}.
 */
public class CachingToolCallback implements ToolCallback {

  private static final Logger log = LoggerFactory.getLogger(CachingToolCallback.class);

  private final ToolCallback delegate;
  private final ToolResultCache cacheService;
  private final boolean cacheEnabled;
  private final long ttlSeconds;
  private final String scope;

  /**
   * Creates a new CachingToolCallback.
   *
   * @param delegate The actual tool callback to wrap.
   * @param cacheService The cache service to store/retrieve results.
   * @param cacheEnabled Whether caching is enabled for this tool.
   * @param ttlSeconds The TTL in seconds for cache entries.
   * @param scope What the actor may see ({@code Actor#scope()}): part of every cache key, so a
   *     result is only ever served to the actor it was computed for (ADR-091).
   */
  public CachingToolCallback(
      ToolCallback delegate,
      ToolResultCache cacheService,
      boolean cacheEnabled,
      long ttlSeconds,
      String scope) {
    this.delegate = delegate;
    this.cacheService = cacheService;
    this.cacheEnabled = cacheEnabled;
    this.ttlSeconds = ttlSeconds;
    this.scope = java.util.Objects.requireNonNull(scope, "scope");
  }

  /**
   * Retrieves the tool definition from the delegate.
   *
   * @return The tool definition containing its name, description, and schemas.
   */
  @Override
  public ToolDefinition getToolDefinition() {
    return delegate.getToolDefinition();
  }

  /**
   * Gets the identifier name of the delegate tool.
   *
   * @return The tool name.
   */
  private String getName() {
    return getToolDefinition().name();
  }

  /**
   * Executes the tool with the provided input parameters. Intercepts the request to check if a
   * cached response exists prior to delegating.
   *
   * @param input The JSON string representation of the tool input parameters.
   * @return The result of the tool execution as a String.
   */
  @Override
  public String call(String input) {
    if (!cacheEnabled) {
      return delegate.call(input);
    }

    // The tool's name and the scope, never the input: a query is the user's content (ADR-064).
    log.debug("Intercepting call to tool '{}'", getName());
    String key = scope + "\n" + input;
    String cached = cacheService.get(getName(), key);
    if (cached != null) {
      log.info("Cache hit for tool '{}'", getName());
      return cached;
    }

    log.debug("Cache miss for tool '{}', executing underlying logic...", getName());
    String result = delegate.call(input);

    log.info("Caching result for tool '{}' with TTL {}s", getName(), ttlSeconds);
    cacheService.put(getName(), key, result, ttlSeconds);

    return result;
  }
}
