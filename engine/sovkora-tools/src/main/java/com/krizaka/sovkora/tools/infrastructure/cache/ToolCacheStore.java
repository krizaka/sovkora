package com.krizaka.sovkora.tools.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolResultCache;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ToolCacheEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ToolCacheId;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository.ToolCacheRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Service managing multi-tier caching with Caffeine and PostgreSQL storage.
 *
 * <p>All caching operations are performed using Spring Data JPA to eliminate raw SQL strings and
 * promote repository-driven patterns (AGENTS.md §2.A).
 */
@Service
public class ToolCacheStore implements ToolResultCache {

  private static final Logger log = LoggerFactory.getLogger(ToolCacheStore.class);

  private final ToolCacheRepository cacheRepository;
  private final Cache<String, CacheEntry> localCache;
  private final java.time.Clock clock;

  /**
   * Represents a cached response entry.
   *
   * @param value The actual string value cached (JSON or payload).
   * @param expiresAt The timestamp when this entry expires.
   */
  public record CacheEntry(String value, Instant expiresAt) {
    /** Compact constructor enforcing non-null expiration. */
    public CacheEntry {
      Objects.requireNonNull(expiresAt, "Expiration timestamp cannot be null");
      if (value == null) {
        value = "";
      }
    }

    /**
     * Checks whether the given instant is past the expiration timestamp.
     *
     * @param now The current instant to check against.
     * @return True if the entry has expired, false otherwise.
     */
    public boolean isExpired(Instant now) {
      return !now.isBefore(expiresAt);
    }
  }

  /**
   * Initializes the ToolCacheStore with a system clock.
   *
   * @param cacheRepository The JPA cache repository for database communication.
   */
  @org.springframework.beans.factory.annotation.Autowired
  public ToolCacheStore(ToolCacheRepository cacheRepository) {
    this(cacheRepository, java.time.Clock.systemUTC());
  }

  /**
   * Initializes the ToolCacheStore with a custom clock.
   *
   * @param cacheRepository The JPA cache repository for database communication.
   * @param clock The clock to use for timestamp generation and comparison.
   */
  public ToolCacheStore(ToolCacheRepository cacheRepository, java.time.Clock clock) {
    this.cacheRepository = cacheRepository;
    this.clock = clock;
    this.localCache = Caffeine.newBuilder().maximumSize(5000).build();
  }

  /**
   * Gets a cached value for a tool and key.
   *
   * @param toolId The ID of the tool.
   * @param cacheKey The cache key string.
   * @return The cached value, or null if expired or not found.
   */
  @Override
  public String get(String toolId, String cacheKey) {
    String fullKey = toolId + ":" + cacheKey;
    Instant now = Instant.now(clock);

    CacheEntry localEntry = localCache.getIfPresent(fullKey);
    if (localEntry != null) {
      if (!localEntry.isExpired(now)) {
        log.debug("Local cache hit for tool={}, key={}", toolId, cacheKey);
        return localEntry.value();
      } else {
        log.debug("Local cache expired for tool={}, key={}", toolId, cacheKey);
        localCache.invalidate(fullKey);
        evictFromDb(toolId, cacheKey);
        return null;
      }
    }

    log.debug("Local cache miss. Checking DB for tool={}, key={}", toolId, cacheKey);
    try {
      Optional<ToolCacheEntity> dbOpt = cacheRepository.findById(new ToolCacheId(toolId, cacheKey));
      if (dbOpt.isPresent()) {
        ToolCacheEntity entity = dbOpt.get();
        CacheEntry dbEntry = new CacheEntry(entity.getCacheValue(), entity.getExpiresAt());
        if (!dbEntry.isExpired(now)) {
          log.debug("DB cache hit for tool={}, key={}", toolId, cacheKey);
          localCache.put(fullKey, dbEntry);
          return dbEntry.value();
        } else {
          log.debug("DB cache expired for tool={}, key={}", toolId, cacheKey);
          evictFromDb(toolId, cacheKey);
        }
      }
    } catch (DataAccessException e) {
      log.error(
          "Failed to query tool cache from database for tool={}, key={}", toolId, cacheKey, e);
    }

    return null;
  }

  /**
   * Puts a value into the local and database cache.
   *
   * @param toolId The ID of the tool.
   * @param cacheKey The cache key.
   * @param value The value to cache.
   * @param ttlSeconds The TTL duration in seconds.
   */
  @Override
  public void put(String toolId, String cacheKey, String value, long ttlSeconds) {
    String fullKey = toolId + ":" + cacheKey;
    Instant now = Instant.now(clock);
    Instant expiresAt = now.plusSeconds(ttlSeconds);
    CacheEntry entry = new CacheEntry(value, expiresAt);

    localCache.put(fullKey, entry);

    try {
      ToolCacheId id = new ToolCacheId(toolId, cacheKey);
      ToolCacheEntity entity = new ToolCacheEntity();
      entity.setId(id);
      entity.setCacheValue(value);
      entity.setExpiresAt(expiresAt);
      entity.setCreatedAt(now);
      cacheRepository.save(entity);
    } catch (DataAccessException e) {
      log.error("Failed to write tool cache to database for tool={}, key={}", toolId, cacheKey, e);
    }
  }

  /**
   * Evicts an entry from the database cache.
   *
   * @param toolId The ID of the tool.
   * @param cacheKey The cache key.
   */
  private void evictFromDb(String toolId, String cacheKey) {
    try {
      cacheRepository.deleteById(new ToolCacheId(toolId, cacheKey));
    } catch (DataAccessException e) {
      log.error(
          "Failed to delete expired cache entry from DB for tool={}, key={}", toolId, cacheKey, e);
    }
  }
}
