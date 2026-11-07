package com.krizaka.sovkora.business.prompt;

import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe, cached resolver for Git-tracked Markdown prompt templates.
 *
 * <p>Reads prompt files from {@code classpath:prompts/{name}.md} through a {@link PromptTextSource}
 * and caches the resolved content in a {@link ConcurrentHashMap} for lock-free, wait-free
 * subsequent reads.
 *
 * <p><b>Concurrency Contract</b>:
 *
 * <ul>
 *   <li>Readers ({@link #resolve}) perform a single {@code ConcurrentHashMap.get()} — lock-free.
 *   <li>Cache misses trigger a single {@code computeIfAbsent()} — at most one I/O per key per JVM.
 *   <li>{@link #evict} and {@link #evictAll} safely invalidate entries without blocking readers.
 * </ul>
 *
 * <p><b>Graceful Degradation</b>: If a prompt file is missing or unreadable, the resolver logs a
 * warning and returns {@link Optional#empty()} — it never throws unhandled classpath exceptions.
 *
 * @since 1.0.0
 */
public final class MarkdownPromptResolver {

  private static final Logger logger = LoggerFactory.getLogger(MarkdownPromptResolver.class);
  private static final String PROMPT_PREFIX = "classpath:prompts/";
  private static final String PROMPT_SUFFIX = ".md";

  private final PromptTextSource source;
  private final ConcurrentHashMap<String, Optional<String>> cache;

  /**
   * Constructs the resolver with the given text source.
   *
   * @param source reads the text behind a {@code classpath:prompts/...} location.
   */
  public MarkdownPromptResolver(PromptTextSource source) {
    if (source == null) {
      throw new IllegalArgumentException("PromptTextSource must not be null");
    }
    this.source = source;
    this.cache = new ConcurrentHashMap<>();
  }

  /**
   * Resolves a named prompt template from the classpath cache.
   *
   * <p>On first access, reads {@code classpath:prompts/{promptName}.md} and caches the result. All
   * subsequent calls return the cached value without I/O.
   *
   * @param promptName The prompt identifier (e.g., "welcome_agent").
   * @return The prompt content, or empty if the file is missing or unreadable.
   */
  public Optional<String> resolve(String promptName) {
    if (promptName == null || promptName.isBlank()) {
      logger.warn("Attempted to resolve a null or blank prompt name.");
      return Optional.empty();
    }
    return cache.computeIfAbsent(promptName, this::loadFromClasspath);
  }

  /**
   * Evicts a single prompt from the cache, forcing a reload on next access.
   *
   * @param promptName The prompt identifier to evict.
   */
  public void evict(String promptName) {
    if (promptName != null) {
      cache.remove(promptName);
      logger.debug("Evicted prompt cache entry: {}", promptName);
    }
  }

  /**
   * Evicts all cached prompts, forcing a full reload on next access.
   *
   * <p>Useful for admin-triggered hot-reload after Git-push of updated prompt files.
   */
  public void evictAll() {
    int size = cache.size();
    cache.clear();
    logger.info("Evicted all {} prompt cache entries.", size);
  }

  /**
   * Returns the current number of cached prompt entries.
   *
   * @return Cache size.
   */
  public int cacheSize() {
    return cache.size();
  }

  /**
   * Loads a prompt file from the classpath.
   *
   * <p>Returns {@link Optional#empty()} if the resource does not exist or cannot be read — graceful
   * degradation over hard failure.
   */
  private Optional<String> loadFromClasspath(String promptName) {
    String location = PROMPT_PREFIX + promptName + PROMPT_SUFFIX;
    try {
      Optional<String> content = source.read(location);
      if (content.isEmpty()) {
        logger.warn("Prompt file not found: {} — returning empty.", location);
      } else {
        logger.debug("Loaded prompt '{}' ({} chars).", promptName, content.get().length());
      }
      return content;
    } catch (UncheckedIOException e) {
      logger.warn("Failed to read prompt file: {} — returning empty.", location, e);
      return Optional.empty();
    }
  }
}
