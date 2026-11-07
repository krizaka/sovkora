package com.krizaka.sovkora.persistence.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine-backed cache configuration for persistence layer read caches.
 *
 * <p>Provides short-TTL in-memory caching for frequently accessed, rarely-mutated data:
 *
 * <ul>
 *   <li>{@code catalogModels} — Model catalog entries (5 min TTL)
 *   <li>{@code catalogModelDefault} — Default model per category (5 min TTL)
 * </ul>
 *
 * <p>Write operations automatically evict cache via {@code @CacheEvict} in the service layer.
 *
 * @since 1.0.0
 */
@Configuration
@EnableCaching
class CacheConfiguration {

  /**
   * Creates a Caffeine-backed {@link CacheManager} with a 5-minute expiration and 500-entry limit.
   *
   * @return The configured cache manager.
   */
  @Bean
  CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager("catalogModels", "catalogModelDefault");
    manager.setCaffeine(
        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(500).recordStats());
    return manager;
  }
}
